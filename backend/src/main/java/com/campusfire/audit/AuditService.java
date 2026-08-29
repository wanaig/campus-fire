package com.campusfire.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class AuditService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(Long operatorId, String action, String targetType, String targetId,
                       Map<String, Object> details) {
        try {
            String json = objectMapper.writeValueAsString(details == null ? Collections.emptyMap() : details);
            jdbcTemplate.update("INSERT INTO audit_log (operator_user_id, action_code, target_type, target_id, detail_json) VALUES (?, ?, ?, ?, ?)",
                    operatorId, action, targetType, targetId, json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计日志序列化失败", exception);
        }
    }
}

