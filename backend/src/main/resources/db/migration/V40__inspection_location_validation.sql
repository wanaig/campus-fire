-- 系统全局设置表：存放管理端可调整的开关类配置（键值对）
CREATE TABLE system_setting (
    setting_key   VARCHAR(64)  PRIMARY KEY COMMENT '设置键',
    setting_value VARCHAR(255) NOT NULL    COMMENT '设置值',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    updated_by    BIGINT       NULL        COMMENT '最近修改的管理员用户ID'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统全局设置';

-- 扫码巡检定位校验开关：true=校验定位（默认，防止代打卡）；false=不校验（GPS漂移误伤时可临时关闭）
INSERT INTO system_setting (setting_key, setting_value) VALUES ('INSPECTION_LOCATION_VALIDATION', 'true');

-- 开关关闭时保安端可不采集定位：巡检会话坐标允许为空
ALTER TABLE inspection_session
    MODIFY latitude  DECIMAL(10, 7) NULL COMMENT '定位纬度（关闭定位校验时可为空）',
    MODIFY longitude DECIMAL(10, 7) NULL COMMENT '定位经度（关闭定位校验时可为空）';

-- 开关关闭时巡检照片坐标同样允许为空
ALTER TABLE inspection_photo
    MODIFY latitude  DECIMAL(10, 7) NULL COMMENT '拍摄纬度（关闭定位校验时可为空）',
    MODIFY longitude DECIMAL(10, 7) NULL COMMENT '拍摄经度（关闭定位校验时可为空）';
