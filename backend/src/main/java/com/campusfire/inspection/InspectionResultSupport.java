package com.campusfire.inspection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检结果 results_json（{item_code: PASS/FAIL}）与部件名称之间的公共转换逻辑，
 * 供巡检会话、巡检记录查询、报表导出、数据看板共用。
 * 保安巡检清单只认「采集员建档勾选登记的档案部件（facility_component）」，
 * 类型检查项（inspection_item）仅作为同类部件的检查标准文案来源，不再擅自扩充清单；
 * 历史记录翻译仍并入类型检查项，避免旧记录里的编码翻不出名称。
 */
@Component
public class InspectionResultSupport {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InspectionResultSupport(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 单个待确认部件：code + 名称 + 检查标准（档案部件无标准文案） */
    public static class CheckItem {
        public final String itemCode;
        public final String itemName;
        public final String standard;

        public CheckItem(String itemCode, String itemName, String standard) {
            this.itemCode = itemCode;
            this.itemName = itemName;
            this.standard = standard == null ? "" : standard;
        }
    }

    /** 设施应逐项确认的部件清单：只认采集员建档勾选的档案部件；同编码的类型检查项仅补充检查标准文案 */
    public LinkedHashMap<String, CheckItem> loadFacilityCheckItems(Long facilityId) {
        LinkedHashMap<String, CheckItem> items = new LinkedHashMap<>();
        List<Map<String, Object>> components = jdbcTemplate.queryForList(
                "SELECT item_code,item_name FROM facility_component WHERE facility_id=? ORDER BY id", facilityId);
        for (Map<String, Object> row : components) {
            String code = String.valueOf(row.get("item_code"));
            items.put(code, new CheckItem(code, String.valueOf(row.get("item_name")), ""));
        }
        // 只补文案不补部件：采集员未勾选的部件（如该设施实际没有的按钮/阀门）不得进入保安巡检清单
        List<Map<String, Object>> configured = jdbcTemplate.queryForList(
                "SELECT i.item_code,i.inspection_standard FROM facility f " +
                        "JOIN inspection_item i ON i.facility_type_id=f.facility_type_id " +
                        "WHERE f.id=? AND i.enabled=1", facilityId);
        for (Map<String, Object> row : configured) {
            String code = String.valueOf(row.get("item_code"));
            CheckItem existing = items.get(code);
            String standard = row.get("inspection_standard") == null ? "" : String.valueOf(row.get("inspection_standard"));
            if (existing != null && existing.standard.isEmpty() && !standard.isEmpty()) {
                items.put(code, new CheckItem(existing.itemCode, existing.itemName, standard));
            }
        }
        return items;
    }

    /** facility_id -> (部件 code -> 名称)：档案部件名优先，并入类型启用检查项，翻译巡检记录时与巡检清单保持一致 */
    public Map<Long, LinkedHashMap<String, String>> loadFacilityLabels(Collection<Long> facilityIds) {
        Map<Long, LinkedHashMap<String, String>> labels = new HashMap<>();
        if (facilityIds == null || facilityIds.isEmpty()) return labels;
        List<Long> ids = new ArrayList<>(facilityIds);
        jdbcTemplate.queryForList(placeholdersIn("SELECT facility_id,item_code,item_name FROM facility_component WHERE facility_id IN", ids), ids.toArray())
                .forEach(row -> labels.computeIfAbsent(((Number) row.get("facility_id")).longValue(), key -> new LinkedHashMap<>())
                        .put(String.valueOf(row.get("item_code")), String.valueOf(row.get("item_name"))));
        jdbcTemplate.queryForList(placeholdersIn("SELECT f.id AS facility_id,i.item_code,i.item_name FROM facility f " +
                "JOIN inspection_item i ON i.facility_type_id=f.facility_type_id WHERE f.id IN", ids) +
                " AND i.enabled=1 ORDER BY f.id,i.sort_order,i.id", ids.toArray())
                .forEach(row -> labels.computeIfAbsent(((Number) row.get("facility_id")).longValue(), key -> new LinkedHashMap<>())
                        .putIfAbsent(String.valueOf(row.get("item_code")), String.valueOf(row.get("item_name"))));
        return labels;
    }

    private String placeholdersIn(String prefix, List<Long> ids) {
        return prefix + " (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
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
