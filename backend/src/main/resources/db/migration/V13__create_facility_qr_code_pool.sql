-- 空白设施二维码池：管理端批量预生成，打印后张贴到设备，由采集员扫码认领建档
CREATE TABLE facility_qr_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(64) NOT NULL,
    serial_no INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UNCLAIMED',
    facility_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bound_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_qr_token (token),
    UNIQUE KEY uk_qr_serial (serial_no),
    KEY idx_qr_status (status),
    CONSTRAINT fk_qr_facility FOREIGN KEY (facility_id) REFERENCES facility(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设施二维码池';

-- 按新流程重建：设施档案一律由采集员扫空白码建立，清空演示设施及关联业务数据
DELETE FROM inspection_photo;
DELETE FROM inspection_draft;
DELETE FROM inspection_record_correction;
DELETE FROM inspection_risk_flag;
DELETE FROM rectification_order;
DELETE FROM inspection_record;
DELETE FROM inspection_session;
DELETE FROM inspection_task;
DELETE FROM maintenance_record;
DELETE FROM facility_action_suggestion;
DELETE FROM facility_lifecycle_event;
DELETE FROM facility;
