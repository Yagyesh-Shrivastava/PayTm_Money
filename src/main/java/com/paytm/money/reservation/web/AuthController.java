package com.paytm.money.reservation.web;

import com.paytm.money.reservation.auth.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private JwtProvider jwtProvider;

    @Value("${ADMIN_SECRET}")
    private String adminSecret;

    @PostMapping("/auth/token")
    public ResponseEntity<?> createToken(@RequestBody Map<String, String> request) {
        String userId = request.get("user_id");
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", "user_id is required"));
        }

        String providedSecret = request.get("X-Admin-Secret");
        boolean isAdmin = false;

        if (providedSecret != null) {
            if (providedSecret.equals(adminSecret)) {
                isAdmin = true;
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden", "message", "Invalid admin secret"));
            }
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("isAdmin", isAdmin);

        String token = jwtProvider.createToken(userId, claims);
        return ResponseEntity.ok(Map.of("token", token));
    }
}
