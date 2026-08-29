CREATE TABLE facility_component (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    manufacture_date DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_facility_component (facility_id,item_code),
    CONSTRAINT fk_fac_component_facility FOREIGN KEY (facility_id) REFERENCES facility(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设施位置包含的部件及生产日期（采集员建档勾选）';
