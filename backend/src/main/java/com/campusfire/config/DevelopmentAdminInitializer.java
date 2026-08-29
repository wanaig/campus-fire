package com.campusfire.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class DevelopmentAdminInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentAdminInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String username;
    private final String password;

    public DevelopmentAdminInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                                       @Value("${app.seed-admin.enabled:false}") boolean enabled,
                                       @Value("${app.seed-admin.username:admin}") String username,
                                       @Value("${app.seed-admin.password:}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) {
            return;
        }
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_role WHERE role_code = 'ADMIN'", Long.class);
        jdbcTemplate.update("INSERT INTO app_user " +
                        "(username, display_name, password_hash, role_id, enabled, accessibility_mode) " +
                        "VALUES (?, ?, ?, ?, 1, 0)",
                username, "系统管理员", passwordEncoder.encode(password), roleId);
        log.warn("Development administrator '{}' was created. Change the password before deployment.", username);
    }
}

