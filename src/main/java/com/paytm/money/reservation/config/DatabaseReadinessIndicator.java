package com.paytm.money.reservation.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class DatabaseReadinessIndicator implements HealthIndicator {

    @Autowired
    private DataSource healthDataSource;

    @Override
    public Health health() {
        try (Connection connection = healthDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT 1");
            return Health.up().withDetail("database", "reachable").build();
        } catch (Exception e) {
            return Health.down().withDetail("database", "unreachable").withException(e).build();
        }
    }
}
