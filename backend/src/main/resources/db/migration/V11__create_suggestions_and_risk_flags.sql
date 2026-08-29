CREATE TABLE facility_action_suggestion (
 id BIGINT NOT NULL AUTO_INCREMENT, facility_id BIGINT NOT NULL, suggestion_type VARCHAR(32) NOT NULL,
 reason VARCHAR(1000) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'OPEN', action_note VARCHAR(1000) NULL,
 action_by BIGINT NULL, action_at DATETIME NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), KEY idx_suggestion_status(status), CONSTRAINT fk_suggestion_facility FOREIGN KEY(facility_id) REFERENCES facility(id), CONSTRAINT fk_suggestion_user FOREIGN KEY(action_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设施更新报废建议';

CREATE TABLE inspection_risk_flag (
 id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, risk_type VARCHAR(64) NOT NULL,
 evidence VARCHAR(2000) NOT NULL, session_id VARCHAR(36) NULL, status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
 reviewed_by BIGINT NULL, reviewed_at DATETIME NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), KEY idx_risk_status(status), KEY idx_risk_user(user_id), CONSTRAINT fk_risk_user FOREIGN KEY(user_id) REFERENCES app_user(id), CONSTRAINT fk_risk_reviewer FOREIGN KEY(reviewed_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检异常行为风险标记';
