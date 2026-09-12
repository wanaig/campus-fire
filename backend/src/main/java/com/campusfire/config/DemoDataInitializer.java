package com.campusfire.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 开发/演示环境数据初始化：创建保安、采集员演示账号。
 * 设施档案一律由采集员扫描管理端预生成的空白二维码建档，此处不再预置演示设施。
 * 正式部署时设置 SEED_DEMO_ENABLED=false 关闭。
 */
@Component
@Order(200)
public class DemoDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String guardPassword;
    private final String collectorPassword;

    public DemoDataInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                               @Value("${app.seed-demo.enabled:true}") boolean enabled,
                               @Value("${app.seed-demo.guard-password:123456}") String guardPassword,
                               @Value("${app.seed-demo.collector-password:123456}") String collectorPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.guardPassword = guardPassword;
        this.collectorPassword = collectorPassword;
    }

    @Override
    public void run(String... args) {
        if (!enabled) return;
        for (int index = 1; index <= 10; index++) {
            String suffix = String.format("%02d", index);
            ensureUser("guard" + suffix, "保安" + suffix, "GUARD", guardPassword);
            ensureUser("collector" + suffix, "采集员" + suffix, "COLLECTOR", collectorPassword);
        }
        log.warn("演示账号已初始化：保安 guard01-guard10 / 采集员 collector01-collector10。正式部署请设置 SEED_DEMO_ENABLED=false。");
    }

    private Long ensureUser(String username, String displayName, String roleCode, String password) {
        List<Long> existing = jdbcTemplate.queryForList("SELECT id FROM app_user WHERE username=?", Long.class, username);
        if (!existing.isEmpty()) return existing.get(0);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM app_role WHERE role_code=?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO app_user(username,display_name,password_hash,role_id,enabled) VALUES(?,?,?,?,1)",
                username, displayName, passwordEncoder.encode(password), roleId);
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
