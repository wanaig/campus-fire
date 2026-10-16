package com.campusfire.common.setting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统全局设置：以 system_setting 键值表为存储，进程内做短时缓存（5 秒）。
 * 管理端修改后最迟 5 秒内对巡检端生效，避免每次扫码都查库。
 */
@Service
public class SystemSettingService {
    /** 扫码巡检定位校验开关的键名：true=校验（默认）；false=不校验 */
    public static final String KEY_INSPECTION_LOCATION_VALIDATION = "INSPECTION_LOCATION_VALIDATION";

    private static final long CACHE_TTL_MILLIS = 5000;

    private final JdbcTemplate jdbcTemplate;
    private volatile Map<String, String> cache = Collections.emptyMap();
    private volatile long cacheLoadedAt = 0;

    public SystemSettingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 读取设置值，缺失时返回默认值 */
    public String get(String key, String defaultValue) {
        Map<String, String> snapshot = snapshot();
        String value = snapshot.get(key);
        return value == null ? defaultValue : value;
    }

    /** 扫码巡检是否校验定位（仅显式配置为 false 时关闭，其余一律视为开启） */
    public boolean isLocationValidationEnabled() {
        return !"false".equalsIgnoreCase(get(KEY_INSPECTION_LOCATION_VALIDATION, "true"));
    }

    /** 更新定位校验开关，并记录操作人 */
    public void setLocationValidationEnabled(boolean enabled, Long updatedBy) {
        write(KEY_INSPECTION_LOCATION_VALIDATION, String.valueOf(enabled), updatedBy);
    }

    /** 写入设置并立即使缓存失效，保证管理端保存后立刻生效 */
    public synchronized void write(String key, String value, Long updatedBy) {
        jdbcTemplate.update("INSERT INTO system_setting(setting_key,setting_value,updated_by) VALUES(?,?,?) " +
                        "ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_by=VALUES(updated_by)",
                key, value, updatedBy);
        cacheLoadedAt = 0;
    }

    private Map<String, String> snapshot() {
        long now = System.currentTimeMillis();
        Map<String, String> current = cache;
        if (now - cacheLoadedAt < CACHE_TTL_MILLIS && !current.isEmpty()) {
            return current;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - cacheLoadedAt < CACHE_TTL_MILLIS && !cache.isEmpty()) {
                return cache;
            }
            Map<String, String> loaded = new HashMap<>();
            try {
                for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT setting_key,setting_value FROM system_setting")) {
                    loaded.put(String.valueOf(row.get("setting_key")), String.valueOf(row.get("setting_value")));
                }
            } catch (RuntimeException ignored) {
                // 表暂不可用时沿用旧缓存，避免巡检被设置读取失败阻断
            }
            cache = loaded;
            cacheLoadedAt = System.currentTimeMillis();
            return loaded;
        }
    }
}
