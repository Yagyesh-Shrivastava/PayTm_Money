package com.paytm.money.reservation.web;

import org.springframework.http.HttpStatus;

public class ReservationException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public ReservationException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}
