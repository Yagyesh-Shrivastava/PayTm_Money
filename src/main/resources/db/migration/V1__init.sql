CREATE TABLE shows (
  show_id CHAR(36) PRIMARY KEY,
  show_name VARCHAR(255) NOT NULL,
  show_price_paise BIGINT NOT NULL,
  per_user_limit INT NOT NULL DEFAULT 4,
  total_seats INT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE seats (
  seat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  show_id CHAR(36) NOT NULL,
  seat_number VARCHAR(32) NOT NULL,
  seat_status VARCHAR(16) NOT NULL DEFAULT 'available',
  reservation_id CHAR(36) NULL,
  UNIQUE KEY uq_show_seat (show_id, seat_number),
  KEY idx_show_status (show_id, seat_status),
  CONSTRAINT fk_seats_show FOREIGN KEY (show_id) REFERENCES shows(show_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE reservations (
  reservation_id CHAR(36) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  show_id CHAR(36) NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  seats_list VARCHAR(2048) NOT NULL,
  seat_count INT NOT NULL,
  amount_paise BIGINT NOT NULL,
  reservation_status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_user_idem (user_id, idempotency_key),
  CONSTRAINT fk_res_show FOREIGN KEY (show_id) REFERENCES shows(show_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE user_show_holds (
  show_id CHAR(36) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  seats_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (show_id, user_id),
  CONSTRAINT fk_holds_show FOREIGN KEY (show_id) REFERENCES shows(show_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
