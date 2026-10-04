package com.paytm.money.reservation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReservationMetrics {

    private final Counter confirmedCounter;
    private final Counter cancelledCounter;
    private final MeterRegistry registry;

    public ReservationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.confirmedCounter = registry.counter("reservations_confirmed_total");
        this.cancelledCounter = registry.counter("reservations_cancelled_total");
    }

    public void incrementConfirmed() {
        confirmedCounter.increment();
    }

    public void incrementCancelled() {
        cancelledCounter.increment();
    }

    public void incrementDeclined(String reason) {
        registry.counter("reservations_declined_total", List.of(Tag.of("reason", reason))).increment();
    }
}
