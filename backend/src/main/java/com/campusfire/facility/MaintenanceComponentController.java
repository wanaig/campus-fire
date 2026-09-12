package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部件级维护保养：保安对设施某个部件执行保养(MAINTAIN)或更换(REPLACE)，到期时间按部件计算。
 * 与巡检分离：巡检只检查正常/异常；保养到期的部件由本控制器提示和登记。
 */
@RestController
@RequestMapping("/maintenance/components")
public class MaintenanceComponentController {
    /** 即将到期提醒窗口：到期前 7 天内视为「即将到期」 */
    private static final int DUE_SOON_DAYS = 7;

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AuditService auditService;

    public MaintenanceComponentController(JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.auditService = auditService;
    }

    /**
     * 部件到期计算（与 FacilityRepository.refreshNextMaintenance 的周期口径一致）：
     * 部件生产日期 + 适用保养周期；无生产日期或无周期的部件返回 NULL（未设置到期，不提醒）。
     */
    private static final String NEXT_DUE =
            "CASE WHEN ii.maintenance_year_threshold IS NOT NULL " +
            "THEN CASE WHEN YEAR(fc.manufacture_date)<ii.maintenance_year_threshold " +
            "THEN ii.maintenance_cycle_before_months ELSE ii.maintenance_cycle_after_months END " +
            "ELSE ii.maintenance_cycle_months END";

    /** 全量部件视图：facility_component JOIN inspection_item 取周期与到期日 */
    private static final String COMPONENT_VIEW =
            "SELECT fc.id AS component_id,fc.facility_id,f.facility_no,f.name,f.campus,f.building,f.floor,f.area, " +
            "fc.item_code,fc.item_name,fc.manufacture_date," + NEXT_DUE + " AS cycle_months, " +
            "CASE WHEN fc.manufacture_date IS NOT NULL AND (" + NEXT_DUE + ") IS NOT NULL " +
            "THEN TIMESTAMPADD(MONTH,(" + NEXT_DUE + "),COALESCE((SELECT MAX(m.maintenance_at) FROM maintenance_record m WHERE m.facility_id=fc.facility_id AND m.component_code=fc.item_code),fc.manufacture_date)) END AS next_due_at, " +
            "(SELECT MAX(m.maintenance_at) FROM maintenance_record m WHERE m.facility_id=fc.facility_id AND m.component_code=fc.item_code) AS last_maintained_at " +
            "FROM facility_component fc " +
            "JOIN facility f ON f.id=fc.facility_id " +
            "LEFT JOIN inspection_item ii ON ii.facility_type_id=f.facility_type_id AND ii.item_code=fc.item_code " +
            "WHERE f.lifecycle_status='IN_USE' AND fc.manufacture_date IS NOT NULL AND (" + NEXT_DUE + ") IS NOT NULL";

    /** 到期/即将到期部件清单（打开小程序提醒用；登录即可查看，保养提交仅限保安） */
    @GetMapping("/due")
    public ApiResponse<List<Map<String, Object>>> due(Authentication authentication) {
        requireLogin(authentication);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                COMPONENT_VIEW + " HAVING next_due_at IS NOT NULL AND next_due_at <= DATE_ADD(CURDATE(), INTERVAL " + DUE_SOON_DAYS + " DAY) ORDER BY next_due_at ASC");
        decorate(rows);
        return ApiResponse.success(rows);
    }

    /** 单个设施的部件保养状态：扫码进入保养页展示；到期部件才可提交保养 */
    @GetMapping("/facilities/{facilityId}")
    public ApiResponse<List<Map<String, Object>>> byFacility(@PathVariable long facilityId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            COMPONENT_VIEW + " AND fc.facility_id=? ORDER BY fc.id", facilityId);
        decorate(rows);
        return ApiResponse.success(rows);
    }

    /** 提交部件保养/更换：仅保安；仅到期/即将到期部件可保养；更换可回填新生产日期并重算到期 */
    @PostMapping
    @Transactional
    public ApiResponse<?> submit(@Valid @RequestBody MaintenanceRequest request, Authentication authentication) {
        AuthenticatedUser user = requireGuard(authentication);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                COMPONENT_VIEW + " AND fc.id=?", request.componentId);
        if (rows.isEmpty()) throw new IllegalArgumentException("部件不存在或设施已停用");
        Map<String, Object> component = rows.get(0);
        Object nextDue = component.get("next_due_at");
        java.time.LocalDate nextDueDate = toLocalDate(nextDue);
        if (nextDueDate == null || nextDueDate.isAfter(java.time.LocalDate.now().plusDays(DUE_SOON_DAYS))) {
            throw new IllegalArgumentException("该部件尚未到保养时间，到期前7天内才能进行保养");
        }
        if (!"MAINTAIN".equals(request.maintenanceType) && !"REPLACE".equals(request.maintenanceType)) {
            throw new IllegalArgumentException("维护类型仅支持保养或更换");
        }
        Long facilityId = ((Number) component.get("facility_id")).longValue();
        String itemCode = String.valueOf(component.get("item_code"));
        String itemName = String.valueOf(component.get("item_name"));
        jdbcTemplate.update("INSERT INTO maintenance_record(facility_id,maintenance_type,component_code,component_name,maintenance_at,maintainer,result_note,created_by) VALUES(?,?,?,?,NOW(),?,?,?)",
                facilityId, request.maintenanceType, itemCode, itemName, user.getDisplayName(), request.resultNote, user.getId());
        if ("REPLACE".equals(request.maintenanceType) && request.newManufactureDate != null) {
            jdbcTemplate.update("UPDATE facility_component SET manufacture_date=? WHERE id=?", request.newManufactureDate, request.componentId);
        }
        // 保养完成后重算该设施下次保养时间（部件级取最早到期；无部件周期回退设施级规则）
        jdbcTemplate.update("UPDATE facility f LEFT JOIN facility_update_rule r ON r.id=f.update_rule_id SET f.next_maintenance_at=COALESCE(" +
                "(SELECT MIN(TIMESTAMPADD(MONTH," + NEXT_DUE + ",COALESCE((SELECT MAX(m.maintenance_at) FROM maintenance_record m WHERE m.facility_id=fc.facility_id AND m.component_code=fc.item_code),fc.manufacture_date))) FROM facility_component fc " +
                "LEFT JOIN inspection_item ii ON ii.facility_type_id=f.facility_type_id AND ii.item_code=fc.item_code " +
                "WHERE fc.facility_id=f.id AND fc.manufacture_date IS NOT NULL AND (" + NEXT_DUE + ") IS NOT NULL), " +
                "CASE WHEN r.maintenance_cycle_months IS NOT NULL AND COALESCE(f.commissioned_date,f.manufacture_date) IS NOT NULL " +
                "THEN DATE_ADD(COALESCE(f.commissioned_date,f.manufacture_date), INTERVAL r.maintenance_cycle_months MONTH) END, " +
                "f.next_maintenance_at) WHERE f.id=?", facilityId);
        jdbcTemplate.update("UPDATE facility SET last_maintenance_at=NOW() WHERE id=?", facilityId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("componentName", itemName);
        details.put("type", request.maintenanceType);
        auditService.record(user.getId(), "MAINTENANCE_COMPONENT", "FACILITY", String.valueOf(facilityId), details);
        return ApiResponse.success(Collections.singletonMap("saved", true));
    }

    private void decorate(List<Map<String, Object>> rows) {
        java.time.LocalDate today = java.time.LocalDate.now();
        for (Map<String, Object> row : rows) {
            java.time.LocalDate dueDate = toLocalDate(row.get("next_due_at"));
            String status = "NONE";
            long overdueDays = 0;
            if (dueDate != null) {
                if (dueDate.isBefore(today)) {
                    status = "OVERDUE";
                    overdueDays = java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
                } else if (!dueDate.isAfter(today.plusDays(DUE_SOON_DAYS))) {
                    status = "DUE_SOON";
                } else {
                    status = "NORMAL";
                }
            }
            row.put("dueStatus", status);
            row.put("overdueDays", overdueDays);
        }
    }

    /** JDBC 日期类型兼容转换：TIMESTAMPADD 结果可能返回 java.sql.Date，MAX(datetime) 返回 Timestamp */
    private java.time.LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate();
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        if (value instanceof java.util.Date) return new java.sql.Timestamp(((java.util.Date) value).getTime()).toLocalDateTime().toLocalDate();
        if (value instanceof java.time.LocalDate) return (java.time.LocalDate) value;
        return java.time.LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private AuthenticatedUser requireGuard(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"GUARD".equals(user.getRoleCode())) throw new AccessDeniedException("仅保安账号可以执行部件保养");
        return user;
    }

    private void requireLogin(Authentication authentication) {
        authService.loadCurrentUser(authentication.getName());
    }

    public static class MaintenanceRequest {
        @NotNull public Long componentId;
        @NotBlank public String maintenanceType;
        @NotBlank public String resultNote;
        /** 更换部件时的新生产日期（选填，填了则重算该部件保养周期） */
        public java.time.LocalDate newManufactureDate;
    }
}
