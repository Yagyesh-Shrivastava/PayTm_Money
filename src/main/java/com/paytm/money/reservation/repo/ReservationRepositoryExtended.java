package com.paytm.money.reservation.repo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ReservationRepositoryExtended extends ReservationRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<Map<String, Object>> findReservationById(String reservationId) {
        String sql = "SELECT * FROM reservations WHERE reservation_id = ?";
        return jdbcTemplate.queryForList(sql, reservationId).stream().findFirst();
    }

    public void updateReservationStatus(String reservationId, String status) {
        String sql = "UPDATE reservations SET reservation_status = ? WHERE reservation_id = ?";
        jdbcTemplate.update(sql, status, reservationId);
    }

    public void releaseSeats(List<Long> seatIds, String reservationId) {
        String sql = "UPDATE seats SET seat_status = 'available', reservation_id = NULL " +
                     "WHERE seat_id IN (" + String.join(",", Collections.nCopies(seatIds.size(), "?")) +
                     ") AND reservation_id = ?";

        List<Object> params = new ArrayList<>();
        params.addAll(seatIds);
        params.add(reservationId);

        jdbcTemplate.update(sql, params.toArray());
    }

    public void decrementUserHold(String showId, String userId, int count) {
        String sql = "UPDATE user_show_holds SET seats_count = GREATEST(0, seats_count - ?) WHERE show_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, count, showId, userId);
    }
}
