package com.paytm.money.reservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public enum ReservationException {
    SEAT_TAKEN(HttpStatus.CONFLICT, "One or more seats are already taken"),
    USER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Per-user seat limit exceeded"),
    IDEMPOTENCY_MISMATCH(HttpStatus.CONFLICT, "Idempotency key reused with different payload"),
    CONNECTION_TIMEOUT(HttpStatus.TOO_MANY_REQUESTS, "Server busy, connection pool saturated"),
    LOCK_TIMEOUT(HttpStatus.CONFLICT, "Seat or user lock acquisition timeout. Please retry"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request payload or missing fields"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication failed"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found");

    private final HttpStatus status;
    private final String message;

    ReservationException(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
