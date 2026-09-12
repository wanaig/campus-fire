-- 巡检项可按部件生产年份分段设置保养周期。
-- 分界年份之前使用 before 周期；分界年份当年及以后使用 after 周期。
ALTER TABLE inspection_item
    ADD COLUMN maintenance_year_threshold SMALLINT NULL COMMENT '保养周期分界年份（该年份前/该年份及以后）' AFTER maintenance_cycle_months,
    ADD COLUMN maintenance_cycle_before_months INT NULL COMMENT '分界年份前的保养周期（月）' AFTER maintenance_year_threshold,
    ADD COLUMN maintenance_cycle_after_months INT NULL COMMENT '分界年份当年及以后的保养周期（月）' AFTER maintenance_cycle_before_months;
