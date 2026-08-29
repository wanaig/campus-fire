package com.campusfire.inspection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 巡检结果 results_json（{item_code: PASS/FAIL}）与检查项名称之间的公共转换逻辑，
 * 供巡检记录查询、报表导出、数据看板共用。
 */
@Component
public class InspectionResultSupport {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InspectionResultSupport(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** facility_type_id -> (item_code -> item_name)，按配置排序返回 */
    public Map<Long, LinkedHashMap<String, String>> loadItemLabels() {
        return jdbcTemplate.query(
                "SELECT facility_type_id,item_code,item_name FROM inspection_item ORDER BY facility_type_id,sort_order,id",
                resultSet -> {
                    Map<Long, LinkedHashMap<String, String>> labels = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        labels.computeIfAbsent(resultSet.getLong("facility_type_id"), key -> new LinkedHashMap<>())
                                .put(resultSet.getString("item_code"), resultSet.getString("item_name"));
                    }
                    return labels;
                });
    }

    public Map<String, String> parseResults(Object resultsJson) {
        if (resultsJson == null) return Collections.emptyMap();
        try {
            return objectMapper.readValue(String.valueOf(resultsJson), Map.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
