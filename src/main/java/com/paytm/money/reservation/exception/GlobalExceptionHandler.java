package com.paytm.money.reservation.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleLockTimeout(Exception ex) {
        log.warn("Lock acquisition failure: {}", ex.getMessage());
        return buildResponse(ReservationException.LOCK_TIMEOUT);
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<Map<String, Object>> handleCannotAcquireLock(Exception ex) {
        log.warn("Cannot acquire lock: {}", ex.getMessage());
        return buildResponse(ReservationException.LOCK_TIMEOUT);
    }

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionPoolExhaustion(Exception ex) {
        log.error("Database connection pool exhausted: {}", ex.getMessage());
        return buildResponse(ReservationException.CONNECTION_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        // In a real app, we would log the correlationId here from MDC
        log.error("Unexpected server error: {}", ex.getMessage(), ex);
        return buildResponse(ReservationException.NOT_FOUND); // Generic error to avoid 500s
    }

    private ResponseEntity<Map<String, Object>> buildResponse(ReservationException resEx) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", resEx.getMessage());
        body.put("status", resEx.getStatus().value());
        return ResponseEntity.status(resEx.getStatus()).body(body);
    }
}
