package com.paytm.money.reservation.repo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ShowRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String createShow(String name, Long price, int limit, int totalSeats) {
        String showId = UUID.randomUUID().toString();
        String sql = "INSERT INTO shows (show_id, show_name, show_price_paise, per_user_limit, total_seats) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, showId, name, price, limit, totalSeats);
        return showId;
    }

    @Transactional
    public void createSeats(String showId, List<String> seatNumbers) {
        String sql = "INSERT INTO seats (show_id, seat_number, seat_status) VALUES (?, ?, 'available')";

        // Use batch update for performance and to avoid N queries
        List<Object[]> batchArgs = new ArrayList<>();
        for (String seat : seatNumbers) {
            batchArgs.add(new Object[]{showId, seat});
        }

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public java.util.Optional<java.util.Map<String, Object>> findShowById(String showId) {
        String sql = "SELECT * FROM shows WHERE show_id = ?";
        return jdbcTemplate.queryForList(sql, showId).stream().findFirst();
    }

    public List<Object[]> getSeatsByShow(String showId) {
        String sql = "SELECT seat_number, seat_status FROM seats WHERE show_id = ? ORDER BY seat_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString("seat_number"),
            rs.getString("seat_status")
        }, showId);
    }
}
