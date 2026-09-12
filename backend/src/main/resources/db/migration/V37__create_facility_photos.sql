-- 采集员设施初始照片留痕：与巡检照片（inspection_photo）同级的建档证据链。
-- 采集员建档/编辑档案时现场拍摄，写入前加水印（设施、位置、采集人、时间、GPS），
-- 原图与水印图分开保存并记录 SHA-256 哈希，存储后端标记 LOCAL / RUSTFS。
-- 拍摄时间窗口放宽到 30 分钟：采集表单填写耗时比巡检长，照片在保存档案时才随表单一起上传。
CREATE TABLE facility_photo (
    id VARCHAR(36) NOT NULL,
    facility_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    original_storage_path VARCHAR(512) NULL,
    storage_backend VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    watermark_sha256 VARCHAR(64) NULL,
    capture_source VARCHAR(32) NOT NULL,
    client_captured_at DATETIME(3) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    location_accuracy_meters DECIMAL(10,2) NULL,
    received_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_facility_photo_facility (facility_id),
    KEY idx_facility_photo_sha256 (sha256),
    CONSTRAINT fk_facility_photo_facility FOREIGN KEY (facility_id) REFERENCES facility(id),
    CONSTRAINT fk_facility_photo_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设施建档初始照片留痕';
