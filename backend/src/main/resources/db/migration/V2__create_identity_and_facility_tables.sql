CREATE TABLE app_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色';

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    phone VARCHAR(32) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    accessibility_mode TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    CONSTRAINT fk_app_user_role FOREIGN KEY (role_id) REFERENCES app_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

CREATE TABLE facility_type (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type_code VARCHAR(32) NOT NULL,
    type_name VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_facility_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设施类型';

CREATE TABLE facility (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_no VARCHAR(64) NOT NULL,
    facility_type_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    campus VARCHAR(100) NOT NULL,
    building VARCHAR(100) NOT NULL,
    floor VARCHAR(50) NOT NULL,
    area VARCHAR(100) NOT NULL,
    detail_location VARCHAR(255) NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    qr_token VARCHAR(128) NOT NULL,
    brand VARCHAR(100) NULL,
    model VARCHAR(100) NULL,
    specification VARCHAR(255) NULL,
    manufacture_date DATE NULL,
    commissioned_date DATE NULL,
    update_rule_id BIGINT NULL,
    last_maintenance_at DATETIME NULL,
    next_maintenance_at DATETIME NULL,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'IN_USE',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_facility_no (facility_no),
    UNIQUE KEY uk_facility_qr_token (qr_token),
    KEY idx_facility_location (campus, building, floor),
    KEY idx_facility_status (lifecycle_status),
    CONSTRAINT fk_facility_type FOREIGN KEY (facility_type_id) REFERENCES facility_type(id),
    CONSTRAINT fk_facility_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消防设施档案';

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_user_id BIGINT NULL,
    action_code VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NULL,
    request_id VARCHAR(64) NULL,
    source_ip VARCHAR(64) NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_created_at (created_at),
    KEY idx_audit_target (target_type, target_id),
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';

INSERT INTO app_role (role_code, role_name) VALUES
    ('ADMIN', '管理员'),
    ('GUARD', '保安'),
    ('COLLECTOR', '数据采集员'),
    ('VISITOR', '访客');

INSERT INTO facility_type (type_code, type_name) VALUES
    ('FIRE_HYDRANT', '消防栓'),
    ('EXTINGUISHER', '灭火器'),
    ('OTHER', '其他消防器材');

