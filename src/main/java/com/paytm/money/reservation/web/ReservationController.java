package com.paytm.money.reservation.web;

import com.paytm.money.reservation.dto.*;
import com.paytm.money.reservation.service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/shows/{id}/reserve")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReserveResponse> reserve(
            @PathVariable("id") String showId,
            @Valid @RequestBody ReserveRequest request,
            HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");

        // Handle idempotency key from header if not in body
        String idempotencyKey = request.idempotency_key;
        String headerKey = httpRequest.getHeader("Idempotency-Key");

        if (idempotencyKey == null && headerKey != null) {
            idempotencyKey = headerKey;
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ReservationException("bad_request", "Idempotency key is required", HttpStatus.BAD_REQUEST);
        }

        // If both are present and differ, return 400
        if (request.idempotency_key != null && headerKey != null && !request.idempotency_key.equals(headerKey)) {
            throw new ReservationException("bad_request", "Idempotency key mismatch between body and header", HttpStatus.BAD_REQUEST);
        }

        // Overwrite request key for service layer
        request.idempotency_key = idempotencyKey;

        ReserveResponse response = reservationService.reserve(userId, showId, request);

        // If it was an idempotent replay, we should theoretically add a header.
        // For now, simple 201/200 return.
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
