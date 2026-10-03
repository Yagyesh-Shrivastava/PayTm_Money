package com.paytm.money.reservation;

import com.paytm.money.reservation.dto.*;
import com.paytm.money.reservation.service.*;
import com.paytm.money.reservation.web.ReservationException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class ReservationConcurrencyTests {

    @Container
    public static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ShowService showService;
    @Autowired
    private CancellationService cancellationService;

    private String showId;

    @BeforeEach
    void setup() {
        CreateShowRequest req = new CreateShowRequest();
        req.name = "Test Show";
        req.price_paise = 1000L;
        req.seats = Arrays.asList("A1", "A2", "A3", "A4", "A5");
        req.per_user_limit = 2;
        showId = showService.createShow(req);
    }

    @Test
    void testHotSeatStorm() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger losers = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String userId = "user_" + i;
            executor.submit(() -> {
                try {
                    latch.await();
                    ReserveRequest req = new ReserveRequest();
                    req.seats = Collections.singletonList("A1");
                    req.idempotency_key = UUID.randomUUID().toString();
                    reservationService.reserve(userId, showId, req);
                    winners.incrementAndGet();
                } catch (ReservationException e) {
                    if (e.getStatus() == HttpStatus.CONFLICT) losers.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, winners.get(), "Exactly one user should win the hot seat");
        assertEquals(threadCount - 1, losers.get(), "All others should get a 409 conflict");
    }

    @Test
    void testPerUserLimit() throws InterruptedException {
        String userId = "power_user";
        int requests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger confirmed = new AtomicInteger(0);

        for (int i = 0; i < requests; i++) {
            final int seatIdx = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    ReserveRequest req = new ReserveRequest();
                    req.seats = Collections.singletonList("A" + (seatIdx + 1));
                    req.idempotency_key = UUID.randomUUID().toString();
                    reservationService.reserve(userId, showId, req);
                    confirmed.incrementAndGet();
                } catch (Exception e) {
                    // expected 409s
                }
                return null;
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(2, confirmed.get(), "User should not exceed per_user_limit of 2");
    }

    @Test
    void testIdempotency() {
        String userId = "user1";
        ReserveRequest req = new ReserveRequest();
        req.seats = Collections.singletonList("A1");
        req.idempotency_key = "key123";

        // First call
        ReserveResponse res1 = reservationService.reserve(userId, showId, req);

        // Second call (exact same)
        ReserveResponse res2 = reservationService.reserve(userId, showId, req);

        assertEquals(res1.reservation_id, res2.reservation_id, "Idempotent call must return same reservation ID");
    }
}
