package com.paytm.money.reservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Configuration
public class LoggingConfig {

    @Bean
    public OncePerRequestFilter correlationIdFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                String correlationId = request.getHeader("X-Correlation-ID");
                if (correlationId == null || correlationId.isEmpty()) {
                    correlationId = UUID.randomUUID().toString();
                }
                MDC.put("correlationId", correlationId);
                try {
                    response.setHeader("X-Correlation-ID", correlationId);
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove("correlationId");
                }
            }
        };
    }
}
