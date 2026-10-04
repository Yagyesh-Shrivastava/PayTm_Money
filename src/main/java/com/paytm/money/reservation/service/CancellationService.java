package com.paytm.money.reservation.service;

import com.paytm.money.reservation.repo.ReservationRepositoryExtended;
import com.paytm.money.reservation.web.ReservationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CancellationService {

    @Autowired
    private ReservationRepositoryExtended reservationRepo;
    @Autowired
    private com.paytm.money.reservation.metrics.ReservationMetrics metrics;

    @Transactional
    public void cancelReservation(String reservationId, String userId) {
        // 1. Non-locking read for initial validation
        var resOpt = reservationRepo.findReservationById(reservationId);
        if (resOpt.isEmpty()) {
            throw new ReservationException("not_found", "Reservation not found", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> res = resOpt.get();
        String ownerId = (String) res.get("user_id");
        if (!ownerId.equals(userId)) {
            throw new ReservationException("not_found", "Reservation not found", HttpStatus.NOT_FOUND);
        }

        String showId = (String) res.get("show_id");
        String seatsList = (String) res.get("seats_list");
        int seatCount = ((Number) res.get("seat_count")).intValue();
        String status = (String) res.get("reservation_status");

        // 2. Transactional block with identical lock ordering to Reserve

        // A. Lock User Row first
        reservationRepo.lockUserRow(showId, userId);

        // B. Resolve labels to IDs and lock them in sorted order
        List<String> labels = Arrays.asList(seatsList.split(","));
        List<Long> seatIds = reservationRepo.getSeatIdsForLabels(showId, labels);

        // The lockSeats method in ReservationRepository already sorts and locks
        reservationRepo.lockSeats(seatIds);

        // C. Re-read reservation status under lock to prevent double-cancel
        var currentRes = reservationRepo.findReservationById(reservationId);
        if (currentRes.isPresent() && "cancelled".equals(currentRes.get().get("reservation_status"))) {
            return; // Already cancelled, return 200 as per plan
        }

        // D. Mutate
        reservationRepo.updateReservationStatus(reservationId, "cancelled");
        reservationRepo.releaseSeats(seatIds, reservationId);
        reservationRepo.decrementUserHold(showId, userId, seatCount);
        metrics.incrementCancelled();
    }
}
