CREATE TABLE facility_lifecycle_event (
    id BIGINT NOT NULL AUTO_INCREMENT, facility_id BIGINT NOT NULL, event_type VARCHAR(32) NOT NULL,
    event_note VARCHAR(1000) NULL, event_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, operator_user_id BIGINT NOT NULL,
    PRIMARY KEY (id), KEY idx_lifecycle_facility (facility_id,event_at),
    CONSTRAINT fk_lifecycle_facility FOREIGN KEY (facility_id) REFERENCES facility(id),
    CONSTRAINT fk_lifecycle_user FOREIGN KEY (operator_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设施生命周期记录';

CREATE TABLE maintenance_record (
    id BIGINT NOT NULL AUTO_INCREMENT, facility_id BIGINT NOT NULL, maintenance_type VARCHAR(32) NOT NULL,
    maintenance_at DATETIME NOT NULL, maintainer VARCHAR(100) NOT NULL, result_note VARCHAR(2000) NOT NULL,
    next_maintenance_at DATETIME NULL, evidence_urls JSON NULL, created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_maintenance_facility (facility_id,maintenance_at),
    CONSTRAINT fk_maintenance_facility FOREIGN KEY (facility_id) REFERENCES facility(id),
    CONSTRAINT fk_maintenance_user FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设施维护保养记录';

CREATE TABLE inspection_record_correction (
    id BIGINT NOT NULL AUTO_INCREMENT, record_id VARCHAR(36) NOT NULL, reason VARCHAR(1000) NOT NULL,
    correction_note VARCHAR(2000) NULL, status VARCHAR(32) NOT NULL DEFAULT 'VOIDED', operator_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_correction_record (record_id),
    CONSTRAINT fk_correction_record FOREIGN KEY (record_id) REFERENCES inspection_record(id),
    CONSTRAINT fk_correction_user FOREIGN KEY (operator_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正式巡检记录作废更正记录';

ALTER TABLE rectification_order ADD COLUMN evidence_urls JSON NULL AFTER resolution_note;
