package com.paytm.money.reservation.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // Paths exempt from auth as per plan
        if (path.equals("/auth/token") || path.startsWith("/actuator/") ||
           (path.startsWith("/shows/") && request.getMethod().equalsIgnoreCase("GET"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "missing_or_invalid_token", "Authorization header missing or malformed");
            return;
        }

        try {
            String token = authHeader.substring(7);
            var claims = jwtProvider.parseToken(token);
            request.setAttribute("userId", claims.getSubject());
            request.setAttribute("isAdmin", claims.get("isAdmin", Boolean.class));
        } catch (Exception e) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "missing_or_invalid_token", "Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", errorCode, message);
        response.getWriter().write(body);
    }
}
