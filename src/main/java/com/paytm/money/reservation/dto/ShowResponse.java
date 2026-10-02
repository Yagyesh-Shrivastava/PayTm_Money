package com.paytm.money.reservation.dto;

import java.util.List;

public class ShowResponse {
    public String show_id;
    public String show_name;
    public Long show_price_paise;
    public Integer per_user_limit;
    public Integer total_seats;
    public List<SeatStatus> seats;
    public ShowSummary summary;

    public static class SeatStatus {
        public String seat_number;
        public String seat_status;

        public SeatStatus(String number, String status) {
            this.seat_number = number;
            this.seat_status = status;
        }
    }

    public static class ShowSummary {
        public int available;
        public int held;
        public int confirmed;
        public int total;

        public ShowSummary(int available, int held, int confirmed, int total) {
            this.available = available;
            this.held = held;
            this.confirmed = confirmed;
            this.total = total;
        }
    }
}
