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
 * 部件清单与名称统一优先取采集员建档登记的档案部件（facility_component），
 * 档案未登记部件的存量设施回退该类型启用检查项（inspection_item），与检查项配置联动。
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

    /** 按巡检任务取该设施应逐项确认的部件清单（登记顺序） */
    public LinkedHashMap<String, CheckItem> loadCheckItems(Long taskId) {
        List<Long> facilityIds = jdbcTemplate.queryForList("SELECT facility_id FROM inspection_task WHERE id=?", Long.class, taskId);
        if (facilityIds.isEmpty() || facilityIds.get(0) == null) throw new IllegalArgumentException("巡检任务不存在");
        return loadFacilityCheckItems(facilityIds.get(0));
    }

    /** 设施应逐项确认的部件清单：档案部件优先，无档案部件的存量设施回退类型检查项 */
    public LinkedHashMap<String, CheckItem> loadFacilityCheckItems(Long facilityId) {
        LinkedHashMap<String, CheckItem> items = new LinkedHashMap<>();
        List<Map<String, Object>> components = jdbcTemplate.queryForList(
                "SELECT item_code,item_name FROM facility_component WHERE facility_id=? ORDER BY id", facilityId);
        for (Map<String, Object> row : components) {
            String code = String.valueOf(row.get("item_code"));
            items.put(code, new CheckItem(code, String.valueOf(row.get("item_name")), ""));
        }
        if (!items.isEmpty()) return items;
        List<Map<String, Object>> legacy = jdbcTemplate.queryForList(
                "SELECT i.item_code,i.item_name,i.inspection_standard FROM facility f " +
                        "JOIN inspection_item i ON i.facility_type_id=f.facility_type_id " +
                        "WHERE f.id=? AND i.enabled=1 ORDER BY i.sort_order,i.id", facilityId);
        for (Map<String, Object> row : legacy) {
            String code = String.valueOf(row.get("item_code"));
            items.put(code, new CheckItem(code, String.valueOf(row.get("item_name")),
                    row.get("inspection_standard") == null ? "" : String.valueOf(row.get("inspection_standard"))));
        }
        return items;
    }

    /** facility_id -> (部件 code -> 名称)：优先档案部件名，翻译巡检记录时与建档登记保持一致 */
    public Map<Long, LinkedHashMap<String, String>> loadFacilityLabels(Collection<Long> facilityIds) {
        Map<Long, LinkedHashMap<String, String>> labels = new HashMap<>();
        if (facilityIds == null || facilityIds.isEmpty()) return labels;
        List<Long> ids = new ArrayList<>(facilityIds);
        jdbcTemplate.queryForList(placeholdersIn("SELECT facility_id,item_code,item_name FROM facility_component WHERE facility_id IN", ids), ids.toArray())
                .forEach(row -> labels.computeIfAbsent(((Number) row.get("facility_id")).longValue(), key -> new LinkedHashMap<>())
                        .put(String.valueOf(row.get("item_code")), String.valueOf(row.get("item_name"))));
        List<Long> fallbackIds = new ArrayList<>();
        for (Long id : ids) if (!labels.containsKey(id)) fallbackIds.add(id);
        if (!fallbackIds.isEmpty()) {
            jdbcTemplate.queryForList(placeholdersIn("SELECT f.id AS facility_id,i.item_code,i.item_name FROM facility f " +
                    "JOIN inspection_item i ON i.facility_type_id=f.facility_type_id WHERE f.id IN", fallbackIds) +
                    " AND i.enabled=1 ORDER BY f.id,i.sort_order,i.id", fallbackIds.toArray())
                    .forEach(row -> labels.computeIfAbsent(((Number) row.get("facility_id")).longValue(), key -> new LinkedHashMap<>())
                            .put(String.valueOf(row.get("item_code")), String.valueOf(row.get("item_name"))));
        }
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
