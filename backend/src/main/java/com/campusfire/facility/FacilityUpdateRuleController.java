package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/facility-update-rules")
public class FacilityUpdateRuleController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AuditService auditService;

    public FacilityUpdateRuleController(JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT r.id,r.rule_name,r.facility_type_id,t.type_code AS facility_type,t.type_name AS facility_type_name,r.service_life_years,r.maintenance_cycle_months,r.legal_basis,r.description,r.enabled FROM facility_update_rule r LEFT JOIN facility_type t ON t.id=r.facility_type_id ORDER BY r.enabled DESC,t.type_name,r.rule_name"));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody Request request, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可配置更新与保养规则");
        Long typeId = null;
        if (request.facilityType != null && !request.facilityType.trim().isEmpty()) {
            typeId = jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?", Long.class, request.facilityType.trim());
        }
        jdbcTemplate.update("INSERT INTO facility_update_rule(rule_name,facility_type_id,service_life_years,maintenance_cycle_months,legal_basis,description) VALUES(?,?,?,?,?,?)",
                request.ruleName.trim(), typeId, request.serviceLifeYears, request.maintenanceCycleMonths, request.legalBasis, request.description);
        return ApiResponse.success(jdbcTemplate.queryForMap("SELECT id,rule_name,facility_type_id,service_life_years,maintenance_cycle_months,legal_basis,description,enabled FROM facility_update_rule WHERE id=LAST_INSERT_ID()"));
    }

    public static class Request {
        @NotBlank(message = "请输入规则名称") public String ruleName;
        public String facilityType;
        public Integer serviceLifeYears;
        public Integer maintenanceCycleMonths;
        public String legalBasis;
        public String description;
        public Boolean enabled;
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable long id, @Valid @RequestBody Request request, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可配置更新与保养规则");
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM facility_update_rule WHERE id=?", Integer.class, id);
        if (exists == null || exists == 0) throw new IllegalArgumentException("规则不存在");
        Long typeId = null;
        if (request.facilityType != null && !request.facilityType.trim().isEmpty()) {
            typeId = jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?", Long.class, request.facilityType.trim());
            if (typeId == null) throw new IllegalArgumentException("设施类型不存在");
        }
        jdbcTemplate.update("UPDATE facility_update_rule SET rule_name=?,facility_type_id=?,service_life_years=?,maintenance_cycle_months=?,legal_basis=?,description=?,enabled=? WHERE id=?",
                request.ruleName.trim(), typeId, request.serviceLifeYears, request.maintenanceCycleMonths,
                request.legalBasis, request.description, request.enabled == null || request.enabled ? 1 : 0, id);
        auditService.record(user.getId(), "RULE_UPDATE", "FACILITY_UPDATE_RULE", String.valueOf(id),
                Collections.singletonMap("ruleName", request.ruleName.trim()));
        return ApiResponse.success(Collections.singletonMap("updated", true));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可配置更新与保养规则");
        Integer used = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM facility WHERE update_rule_id=?", Integer.class, id);
        if (used != null && used > 0) throw new IllegalArgumentException("该规则已被设施引用，不能删除，可先停用规则");
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM facility_update_rule WHERE id=?", Integer.class, id);
        if (exists == null || exists == 0) throw new IllegalArgumentException("规则不存在");
        jdbcTemplate.update("DELETE FROM facility_update_rule WHERE id=?", id);
        auditService.record(user.getId(), "RULE_DELETE", "FACILITY_UPDATE_RULE", String.valueOf(id), null);
        return ApiResponse.success(Collections.singletonMap("deleted", true));
    }
}
