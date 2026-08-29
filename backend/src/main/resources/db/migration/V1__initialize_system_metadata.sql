CREATE TABLE system_metadata (
    metadata_key VARCHAR(100) NOT NULL COMMENT '元数据键',
    metadata_value VARCHAR(500) NOT NULL COMMENT '元数据值',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (metadata_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统元数据';

INSERT INTO system_metadata (metadata_key, metadata_value)
VALUES ('schema.version', '1');

