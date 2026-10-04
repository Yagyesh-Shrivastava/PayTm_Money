package com.paytm.money.reservation.dto;

import java.util.List;

public class ReserveResponse {
    public String reservation_id;
    public String show_id;
    public String user_id;
    public List<String> seats;
    public Long amount_paise;
    public String status;

    public ReserveResponse(String reservationId, String showId, String userId, List<String> seats, Long amount, String status) {
        this.reservation_id = reservationId;
        this.show_id = showId;
        this.user_id = userId;
        this.seats = seats;
        this.amount_paise = amount;
        this.status = status;
    }
}
