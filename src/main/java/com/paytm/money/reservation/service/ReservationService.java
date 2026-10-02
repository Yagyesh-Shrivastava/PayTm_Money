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
import org.springframework.transaction.interceptor.PessimisticLockingFailureException;

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
    private ReservationMetrics metrics;

    public ReserveResponse reserve(String userId, String showId, ReserveRequest request) {

    public ReserveResponse reserve(String userId, String showId, ReserveRequest request) {
        // 1. Validation & Normalization
        List<String> labels = request.seats.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (labels.size() != request.seats.size()) {
            throw new ReservationException("bad_request", "Duplicate seats in request", HttpStatus.BAD_REQUEST);
        }

        // Load show (Immutable)
        var showMapOpt = showRepository.findShowById(showId);
        if (showMapOpt.isEmpty()) {
            throw new ReservationException("not_found", "Show not found", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> show = showMapOpt.get();
        int perUserLimit = ((Number) show.get("per_user_limit")).intValue();
        long pricePaise = ((Number) show.get("show_price_paise")).longValue();

        if (labels.size() > perUserLimit) {
            metrics.incrementDeclined("per-user-limit");
            throw new ReservationException("per_user_limit", "Request exceeds per-user limit", HttpStatus.CONFLICT);
        }

        String requestHash = computeHash(showId, labels);
        String idempotencyKey = request.idempotency_key;

        // 2. Idempotency Fast Path (Non-locking)
        var existingRes = reservationRepository.findByIdempotencyKey(userId, idempotencyKey);
        if (existingRes.isPresent()) {
            Map<String, Object> res = existingRes.get();
            if (requestHash.equals(res.get("request_hash"))) {
                metrics.incrementDeclined("idempotent-replay");
                return buildResponseFromMap(res);
            } else {
                metrics.incrementDeclined("key-mismatch");
                throw new ReservationException("idempotency_key_mismatch", "Key used with different request", HttpStatus.CONFLICT);
            }
        }

        // 3. Resolve Labels to IDs & Fast Decline
        List<Long> seatIds = reservationRepository.getSeatIdsForLabels(showId, labels);
        if (seatIds.size() != labels.size()) {
            throw new ReservationException("not_found", "One or more seats not found", HttpStatus.NOT_FOUND);
        }

        for (Long id : seatIds) {
            var statusMap = reservationRepository.getSeatStatus(id);
            if (statusMap != null && "confirmed".equals(statusMap.get("seat_status"))) {
                metrics.incrementDeclined("seat-taken");
                throw new ReservationException("seat_taken", "One or more seats already taken", HttpStatus.CONFLICT);
            }
        }

        // 4. User Row Init (Outside transaction to avoid pool deadlock)
        reservationRepository.initUserRow(showId, userId);

        // 5. Transactional Loop with Retries
        return executeWithRetry(() -> executeReservationTransaction(userId, showId, idempotencyKey, requestHash, labels, seatIds, pricePaise, perUserLimit));
    }

    @Transactional
    protected ReserveResponse executeReservationTransaction(String userId, String showId, String key, String hash, List<String> labels, List<Long> seatIds, long price, int limit) {
        // A. Lock User Row
        reservationRepository.lockUserRow(showId, userId);

        // B. Authoritative Idempotency Check
        var existingRes = reservationRepository.findByIdempotencyKey(userId, key);
        if (existingRes.isPresent()) {
            Map<String, Object> res = existingRes.get();
            if (hash.equals(res.get("request_hash"))) {
                return buildResponseFromMap(res);
            } else {
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
                    Thread.sleep(10 + (long)(Math.random() * 50)); // Jittered backoff
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
            String input = showId + String.join("", labels);
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
