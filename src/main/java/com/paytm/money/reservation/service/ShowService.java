package com.paytm.money.reservation.service;

import com.paytm.money.reservation.dto.CreateShowRequest;
import com.paytm.money.reservation.dto.ShowResponse;
import com.paytm.money.reservation.repo.ShowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ShowService {

    @Autowired
    private ShowRepository showRepository;

    @Transactional
    public String createShow(CreateShowRequest request) {
        // Validate for duplicate labels in the list
        if (request.seats.size() != (new java.util.HashSet<>(request.seats)).size()) {
            throw new com.paytm.money.reservation.web.ReservationException(
                "bad_request", "Duplicate seat labels provided", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        String showId = showRepository.createShow(
            request.name,
            request.price_paise,
            request.per_user_limit,
            request.seats.size()
        );

        showRepository.createSeats(showId, request.seats);
        return showId;
    }

    public ShowResponse getShow(String showId, boolean summaryOnly) {
        var showMapOpt = showRepository.findShowById(showId);
        if (showMapOpt.isEmpty()) {
            throw new com.paytm.money.reservation.web.ReservationException(
                "not_found", "Show not found", org.springframework.http.HttpStatus.NOT_FOUND);
        }

        Map<String, Object> show = showMapOpt.get();
        ShowResponse response = new ShowResponse();
        response.show_id = (String) show.get("show_id");
        response.show_name = (String) show.get("show_name");
        response.show_price_paise = ((Number) show.get("show_price_paise")).longValue();
        response.per_user_limit = ((Number) show.get("per_user_limit")).intValue();
        response.total_seats = ((Number) show.get("total_seats")).intValue();

        // Calculate counts and get seats list
        List<Object[]> seatRows = showRepository.getSeatsByShow(showId);
        int available = 0, held = 0, confirmed = 0;

        List<ShowResponse.SeatStatus> seats = new ArrayList<>();
        for (Object[] row : seatRows) {
            String num = (String) row[0];
            String status = (String) row[1];

            if ("available".equals(status)) available++;
            else if ("held".equals(status)) held++;
            else if ("confirmed".equals(status)) confirmed++;

            if (!summaryOnly) {
                seats.add(new ShowResponse.SeatStatus(num, status));
            }
        }

        response.seats = seats;
        response.summary = new ShowResponse.ShowSummary(available, held, confirmed, response.total_seats);

        return response;
    }
}
