CREATE TABLE shows (
    show_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_name VARCHAR(255) NOT NULL,
    show_price_paise BIGINT NOT NULL,
    per_user_limit INT DEFAULT 4 NOT NULL,
    total_seats INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE seats (
    seat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id BIGINT NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    seat_status ENUM('AVAILABLE', 'HELD', 'CONFIRMED') NOT NULL DEFAULT 'AVAILABLE',
    reservation_id BIGINT NULL,
    CONSTRAINT fk_seats_show FOREIGN KEY (show_id) REFERENCES shows(show_id),
    CONSTRAINT uq_show_seat UNIQUE (show_id, seat_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE reservations (
    reservation_id CHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    show_id BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    seats_list VARCHAR(1000) NOT NULL,
    seat_count INT NOT NULL,
    amount_paise BIGINT NOT NULL,
    reservation_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_res_show FOREIGN KEY (show_id) REFERENCES shows(show_id),
    CONSTRAINT uq_user_idempotency UNIQUE (user_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE user_show_holds (
    user_id VARCHAR(100) NOT NULL,
    show_id BIGINT NOT NULL,
    seats_count INT DEFAULT 0 NOT NULL,
    PRIMARY KEY (user_id, show_id),
    CONSTRAINT fk_holds_show FOREIGN KEY (show_id) REFERENCES shows(show_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
