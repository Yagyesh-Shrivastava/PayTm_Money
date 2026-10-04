package com.paytm.money.reservation.service;

import com.paytm.money.reservation.repo.ReservationRepositoryExtended;
import com.paytm.money.reservation.web.ReservationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CancellationServiceUnitTest {

    @Mock
    private ReservationRepositoryExtended reservationRepo;
    @Mock
    private com.paytm.money.reservation.metrics.ReservationMetrics metrics;

    @InjectMocks
    private CancellationService cancellationService;

    private final String resId = "res-1";
    private final String userId = "user-1";
    private final String showId = "show-1";

    @BeforeEach
    void setup() {
        Map<String, Object> res = new HashMap<>();
        res.put("reservation_id", resId);
        res.put("user_id", userId);
        res.put("show_id", showId);
        res.put("seats_list", "A1,A2");
        res.put("seat_count", 2);
        res.put("reservation_status", "confirmed");

        lenient().when(reservationRepo.findReservationById(resId)).thenReturn(Optional.of(res));
    }

    @Test
    void cancel_NotFound_Throws404() {
        when(reservationRepo.findReservationById("unknown")).thenReturn(Optional.empty());

        ReservationException ex = assertThrows(ReservationException.class,
                () -> cancellationService.cancelReservation("unknown", userId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void cancel_WrongUser_Throws404() {
        ReservationException ex = assertThrows(ReservationException.class,
                () -> cancellationService.cancelReservation(resId, "wrong-user"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void cancel_AlreadyCancelled_ReturnsQuietly() {
        Map<String, Object> cancelledRes = new HashMap<>();
        cancelledRes.put("reservation_id", resId);
        cancelledRes.put("user_id", userId);
        cancelledRes.put("show_id", showId);
        cancelledRes.put("seats_list", "A1,A2");
        cancelledRes.put("seat_count", 2);
        cancelledRes.put("reservation_status", "cancelled");

        // Mock the first read and the locked re-read
        when(reservationRepo.findReservationById(resId)).thenReturn(Optional.of(cancelledRes));

        cancellationService.cancelReservation(resId, userId);

        // Verify no updates were made
        verify(reservationRepo, never()).updateReservationStatus(any(), any());
    }
}
