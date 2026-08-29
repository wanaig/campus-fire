CREATE TABLE inspection_session (
    id VARCHAR(36) NOT NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    qr_token_snapshot VARCHAR(128) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    location_distance_meters DECIMAL(10,2) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'STARTED',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_session_task_user (task_id,user_id),
    CONSTRAINT fk_session_task FOREIGN KEY (task_id) REFERENCES inspection_task(id),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='现场巡检会话';

CREATE TABLE inspection_draft (
    session_id VARCHAR(36) NOT NULL,
    results_json JSON NOT NULL,
    note VARCHAR(1000) NULL,
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    CONSTRAINT fk_draft_session FOREIGN KEY (session_id) REFERENCES inspection_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检草稿';

