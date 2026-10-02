package com.paytm.money.reservation.web;

import com.paytm.money.reservation.dto.CreateShowRequest;
import com.paytm.money.reservation.dto.ShowResponse;
import com.paytm.money.reservation.service.ShowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/shows")
public class ShowController {

    @Autowired
    private ShowService showService;

    @PostMapping
    public ResponseEntity<?> createShow(@Valid @RequestBody CreateShowRequest request, HttpServletRequest httpRequest) {
        // Admin check
        Boolean isAdmin = (Boolean) httpRequest.getAttribute("isAdmin");
        if (isAdmin == null || !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                java.util.Map.of("error", "forbidden", "message", "Admin access required")
            );
        }

        String showId = showService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("show_id", showId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowResponse> getShow(
            @PathVariable("id") String id,
            @RequestParam(value = "summary", defaultValue = "false") boolean summary) {

        return ResponseEntity.ok(showService.getShow(id, summary));
    }
}
