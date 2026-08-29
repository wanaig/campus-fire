package com.campusfire.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> users = jdbcTemplate.query(
                "SELECT u.id, u.username, u.display_name, u.password_hash, r.role_code, " +
                        "u.enabled, u.accessibility_mode FROM app_user u " +
                        "JOIN app_role r ON r.id = u.role_id WHERE u.username = ?",
                (resultSet, rowNumber) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("role_code"),
                        resultSet.getBoolean("enabled"),
                        resultSet.getBoolean("accessibility_mode")),
                username);
        return users.stream().findFirst();
    }
}

