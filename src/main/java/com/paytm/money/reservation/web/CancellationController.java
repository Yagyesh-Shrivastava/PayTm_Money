package com.paytm.money.reservation.web;

import com.paytm.money.reservation.service.CancellationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/reservations/{id}/cancel")
public class CancellationController {

    @Autowired
    private CancellationService cancellationService;

    @PostMapping
    public ResponseEntity<?> cancel(@PathVariable("id") String reservationId, HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");

        cancellationService.cancelReservation(reservationId, userId);

        return ResponseEntity.ok(java.util.Map.of("status", "cancelled"));
    }
}
