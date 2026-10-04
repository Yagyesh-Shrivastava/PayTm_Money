package com.paytm.money.reservation.repo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ReservationRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<Map<String, Object>> findByIdempotencyKey(String userId, String key) {
        String sql = "SELECT * FROM reservations WHERE user_id = ? AND idempotency_key = ?";
        return jdbcTemplate.queryForList(sql, userId, key).stream().findFirst();
    }

    public List<Long> getSeatIdsForLabels(String showId, List<String> labels) {
        String sql = "SELECT seat_id FROM seats WHERE show_id = ? AND seat_number IN (" +
                     String.join(",", Collections.nCopies(labels.size(), "?")) + ")";

        List<Object> params = new ArrayList<>();
        params.add(showId);
        params.addAll(labels);

        return jdbcTemplate.queryForList(sql, Long.class, params.toArray());
    }

    public Map<String, Object> getSeatStatus(Long seatId) {
        String sql = "SELECT seat_status FROM seats WHERE seat_id = ?";
        return jdbcTemplate.queryForList(sql, seatId).stream().findFirst().orElse(null);
    }

    public void lockUserRow(String showId, String userId) {
        String sql = "SELECT seats_count FROM user_show_holds WHERE show_id = ? AND user_id = ? FOR UPDATE";
        jdbcTemplate.queryForList(sql, showId, userId);
    }

    public Integer getUserHoldCount(String showId, String userId) {
        String sql = "SELECT seats_count FROM user_show_holds WHERE show_id = ? AND user_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, showId, userId);
    }

    public void initUserRow(String showId, String userId) {
        String sql = "INSERT INTO user_show_holds (show_id, user_id, seats_count) VALUES (?, ?, 0) " +
                     "ON DUPLICATE KEY UPDATE seats_count = seats_count";
        jdbcTemplate.update(sql, showId, userId);
    }

    public List<Map<String, Object>> lockSeats(List<Long> seatIds) {
        if (seatIds.isEmpty()) return Collections.emptyList();

        // Sort seatIds to prevent deadlocks
        Collections.sort(seatIds);

        String sql = "SELECT seat_id, seat_status FROM seats WHERE seat_id IN (" +
                     String.join(",", Collections.nCopies(seatIds.size(), "?")) + ") ORDER BY seat_id FOR UPDATE";

        return jdbcTemplate.queryForList(sql, seatIds.toArray());
    }

    public void createReservation(String id, String userId, String showId, String key, String hash, String seatsList, int count, long amount, String status) {
        String sql = "INSERT INTO reservations (reservation_id, user_id, show_id, idempotency_key, request_hash, seats_list, seat_count, amount_paise, reservation_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, userId, showId, key, hash, seatsList, count, amount, status);
    }

    public void updateSeatStatus(List<Long> seatIds, String reservationId, String status) {
        String sql = "UPDATE seats SET seat_status = ?, reservation_id = ? WHERE seat_id IN (" +
                     String.join(",", Collections.nCopies(seatIds.size(), "?")) + ")";

        List<Object> params = new ArrayList<>();
        params.add(status);
        params.add(reservationId);
        params.addAll(seatIds);

        jdbcTemplate.update(sql, params.toArray());
    }

    public void incrementUserHold(String showId, String userId, int count) {
        String sql = "UPDATE user_show_holds SET seats_count = seats_count + ? WHERE show_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, count, showId, userId);
    }
}
