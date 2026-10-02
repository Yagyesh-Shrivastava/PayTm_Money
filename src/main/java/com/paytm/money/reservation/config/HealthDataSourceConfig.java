package com.paytm.money.reservation.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class HealthDataSourceConfig {

    @Value("${DB_URL}")
    private String dbUrl;
    @Value("${DB_USER}")
    private String dbUser;
    @Value("${DB_PASSWORD}")
    private String dbPassword;

    @Bean
    public DataSource healthDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(2); // Tiny pool for health checks
        config.setConnectionTimeout(2000); // Fast fail
        config.setPoolName("HikariPool-Health");
        return new HikariDataSource(config);
    }
}
