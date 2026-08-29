package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/facility-types")
public class FacilityTypeController {
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final AuthService authService;

    public FacilityTypeController(JdbcTemplate jdbcTemplate, AuditService auditService, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT id,type_code AS typeCode,type_name AS typeName,enabled FROM facility_type ORDER BY id"));
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody Request request, Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        String code = normalizeCode(request.typeCode);
        try {
            jdbcTemplate.update("INSERT INTO facility_type(type_code,type_name,enabled) VALUES(?,?,1)",
                    code, request.typeName.trim());
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("设施类型编号已存在");
        }
        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT id,type_code AS typeCode,type_name AS typeName,enabled FROM facility_type WHERE id=LAST_INSERT_ID()");
        auditService.record(user.getId(), "FACILITY_TYPE_CREATE", "FACILITY_TYPE",
                String.valueOf(created.get("id")), created);
        return ApiResponse.success(created);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @Valid @RequestBody Request request,
                                                   Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        String code = normalizeCode(request.typeCode);
        try {
            int updated = jdbcTemplate.update("UPDATE facility_type SET type_code=?,type_name=? WHERE id=?",
                    code, request.typeName.trim(), id);
            if (updated == 0) throw new IllegalArgumentException("设施类型不存在");
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("设施类型编号已存在");
        }
        Map<String, Object> result = jdbcTemplate.queryForMap(
                "SELECT id,type_code AS typeCode,type_name AS typeName,enabled FROM facility_type WHERE id=?", id);
        auditService.record(user.getId(), "FACILITY_TYPE_UPDATE", "FACILITY_TYPE", String.valueOf(id), result);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        Map<String, Object> type = find(id);
        int facilityCount = count("SELECT COUNT(*) FROM facility WHERE facility_type_id=?", id);
        int itemCount = count("SELECT COUNT(*) FROM inspection_item WHERE facility_type_id=?", id);
        int planCount = count("SELECT COUNT(*) FROM inspection_plan WHERE facility_type_id=? AND deleted=0", id);
        int ruleCount = count("SELECT COUNT(*) FROM facility_update_rule WHERE facility_type_id=?", id);
        if (facilityCount + itemCount + planCount + ruleCount > 0) {
            throw new IllegalArgumentException(String.format(
                    "该类型仍关联设施 %d 个、检查项 %d 个、巡检计划 %d 个、更新规则 %d 个，请先处理关联数据",
                    facilityCount, itemCount, planCount, ruleCount));
        }
        jdbcTemplate.update("DELETE FROM facility_type WHERE id=?", id);
        auditService.record(user.getId(), "FACILITY_TYPE_DELETE", "FACILITY_TYPE", String.valueOf(id), type);
        return ApiResponse.success(Collections.singletonMap("deleted", true));
    }

    private Map<String, Object> find(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,type_code AS typeCode,type_name AS typeName,enabled FROM facility_type WHERE id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("设施类型不存在");
        return rows.get(0);
    }

    private int count(String sql, long id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count == null ? 0 : count;
    }

    private String normalizeCode(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (code.isEmpty() || code.length() > 32) throw new IllegalArgumentException("设施类型编号需为 1-32 位字母、数字或下划线");
        return code;
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可维护设施类型");
        return user;
    }

    public static class Request {
        @NotBlank(message = "请输入设施类型编号") public String typeCode;
        @NotBlank(message = "请输入设施类型名称") public String typeName;
    }
}
