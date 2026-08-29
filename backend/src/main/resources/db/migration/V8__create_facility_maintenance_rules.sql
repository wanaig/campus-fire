CREATE TABLE facility_update_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_name VARCHAR(100) NOT NULL,
    facility_type_id BIGINT NULL,
    service_life_years INT NULL,
    maintenance_cycle_months INT NULL,
    legal_basis VARCHAR(255) NULL,
    description VARCHAR(500) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_update_rule_type (facility_type_id),
    CONSTRAINT fk_update_rule_type FOREIGN KEY (facility_type_id) REFERENCES facility_type(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消防设施技术更新与保养规则';

INSERT INTO facility_update_rule (rule_name, facility_type_id, service_life_years, maintenance_cycle_months, description)
SELECT CONCAT(t.type_name, '通用规则'), t.id, 10, 6, '具体年限和周期应结合设备厂家说明、现行标准及学校实际情况确认'
FROM facility_type t;
