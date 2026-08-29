CREATE TABLE inspection_record (
    id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    results_json JSON NOT NULL,
    note VARCHAR(1000) NULL,
    photo_count INT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_session (session_id),
    KEY idx_record_task (task_id),
    CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES inspection_session(id),
    CONSTRAINT fk_record_task FOREIGN KEY (task_id) REFERENCES inspection_task(id),
    CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正式巡检记录（提交后不可修改）';
