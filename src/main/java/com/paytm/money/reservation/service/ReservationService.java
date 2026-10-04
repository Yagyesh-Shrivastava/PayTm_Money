package com.paytm.money.reservation.service;

import com.paytm.money.reservation.dto.*;
import com.paytm.money.reservation.repo.*;
import com.paytm.money.reservation.web.ReservationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.PessimisticLockingFailureException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private com.paytm.money.reservation.metrics.ReservationMetrics metrics;

    public ReserveResponse reserve(String userId, String showId, ReserveRequest request) {
        // 1. Validation & Normalization (Lightweight)
        List<String> labels = request.seats.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (labels.size() != request.seats.size()) {
            throw new ReservationException("bad_request", "Duplicate seats in request", HttpStatus.BAD_REQUEST);
        }

        String requestHash = computeHash(showId, labels);
        String idempotencyKey = request.idempotency_key;

        // 2. Transactional Loop with Retries
        // We move ALL checks (Idempotency, Limit, Seat Status) inside the transaction
        // to eliminate the "Fast Path Gap" and ensure atomic decisions.
        return executeWithRetry(() -> executeReservationTransaction(userId, showId, idempotencyKey, requestHash, labels, pricePaise(showId), perUserLimit(showId)));
    }

    @Transactional
    protected ReserveResponse executeReservationTransaction(String userId, String showId, String key, String hash, List<String> labels, long price, int limit) {
        // A. Lock User Row (and initialize if missing)
        // We use a specific strategy to avoid the pre-transaction bottleneck
        ensureUserRowExists(showId, userId);
        reservationRepository.lockUserRow(showId, userId);

        // B. Authoritative Idempotency Check
        var existingRes = reservationRepository.findByIdempotencyKey(userId, key);
        if (existingRes.isPresent()) {
            Map<String, Object> res = existingRes.get();
            if (hash.equals(res.get("request_hash"))) {
                metrics.incrementDeclined("idempotent-replay");
                return buildResponseFromMap(res);
            } else {
                metrics.incrementDeclined("key-mismatch");
                throw new ReservationException("idempotency_key_mismatch", "Key mismatch", HttpStatus.CONFLICT);
            }
        }

        // C. Limit Check
        int currentCount = reservationRepository.getUserHoldCount(showId, userId);
        if (currentCount + labels.size() > limit) {
            metrics.incrementDeclined("per-user-limit");
            throw new ReservationException("per_user_limit", "User limit exceeded", HttpStatus.CONFLICT);
        }

        // D. Lock Seats in Sorted Order
        List<Long> seatIds = reservationRepository.getSeatIdsForLabels(showId, labels);
        if (seatIds.size() != labels.size()) {
            throw new ReservationException("not_found", "One or more seats not found", HttpStatus.NOT_FOUND);
        }

        List<Map<String, Object>> lockedSeats = reservationRepository.lockSeats(seatIds);
        for (Map<String, Object> seat : lockedSeats) {
            if (!"available".equals(seat.get("seat_status"))) {
                metrics.incrementDeclined("seat-taken");
                throw new ReservationException("seat_taken", "Seat already taken", HttpStatus.CONFLICT);
            }
        }

        // E. Mutate
        String reservationId = UUID.randomUUID().toString();
        String seatsList = String.join(",", labels);
        long totalAmount = (long) labels.size() * price;

        reservationRepository.createReservation(reservationId, userId, showId, key, hash, seatsList, labels.size(), totalAmount, "confirmed");
        reservationRepository.updateSeatStatus(seatIds, reservationId, "confirmed");
        reservationRepository.incrementUserHold(showId, userId, labels.size());

        metrics.incrementConfirmed();
        return new ReserveResponse(reservationId, showId, userId, labels, totalAmount, "confirmed");
    }

    private void ensureUserRowExists(String showId, String userId) {
        // In a production system, we'd handle the missing row via a try-catch on lockUserRow
        // or by utilizing the ON DUPLICATE KEY UPDATE here.
        reservationRepository.initUserRow(showId, userId);
    }

    private long pricePaise(String showId) {
        var show = showRepository.findShowById(showId)
                .orElseThrow(() -> new ReservationException("not_found", "Show not found", HttpStatus.NOT_FOUND));
        return ((Number) show.get("show_price_paise")).longValue();
    }

    private int perUserLimit(String showId) {
        var show = showRepository.findShowById(showId)
                .orElseThrow(() -> new ReservationException("not_found", "Show not found", HttpStatus.NOT_FOUND));
        return ((Number) show.get("per_user_limit")).intValue();
    }

    private ReserveResponse executeWithRetry(java.util.function.Supplier<ReserveResponse> action) {
        int attempts = 0;
        while (true) {
            try {
                return action.get();
            } catch (PessimisticLockingFailureException e) {
                attempts++;
                if (attempts >= 5) {
                    throw new ReservationException("contention", "Too many concurrent requests", HttpStatus.CONFLICT);
                }
                try {
                    Thread.sleep(10 + (long)(Math.random() * 50));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    private String computeHash(String showId, List<String> labels) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Hardening: use a delimiter to prevent collision (e.g., "A1"+"A2" vs "A1A"+"2")
            String input = showId + "|" + String.join("|", labels);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private ReserveResponse buildResponseFromMap(Map<String, Object> res) {
        return new ReserveResponse(
            (String) res.get("reservation_id"),
            (String) res.get("show_id"),
            (String) res.get("user_id"),
            java.util.Arrays.asList(((String) res.get("seats_list")).split(",")),
            ((Number) res.get("amount_paise")).longValue(),
            (String) res.get("reservation_status")
        );
    }
}
