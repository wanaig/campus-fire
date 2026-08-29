package com.campusfire.auth;

import com.campusfire.audit.AuditService;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserAdminController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdminController(JdbcTemplate jdbcTemplate, AuthService authService,
                               PasswordEncoder passwordEncoder, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String roleCode,
                                                       Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT u.id,u.username,u.display_name,u.phone,u.enabled,r.role_code AS roleCode,r.role_name AS roleName " +
                        "FROM app_user u JOIN app_role r ON r.id=u.role_id " +
                        "WHERE (? IS NULL OR r.role_code=?) ORDER BY u.id",
                roleCode, roleCode));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest request, Authentication authentication) {
        AuthenticatedUser operator = requireAdmin(authentication);
        String username = request.username.trim();
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?", Integer.class, username);
        if (existing != null && existing > 0) throw new IllegalArgumentException("账号已存在");
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM app_role WHERE role_code=?", Long.class, request.roleCode);
        if (roleId == null) throw new IllegalArgumentException("角色不存在");
        jdbcTemplate.update("INSERT INTO app_user(username,display_name,password_hash,role_id,phone,enabled) VALUES(?,?,?,?,?,1)",
                username, request.displayName.trim(), passwordEncoder.encode(request.password), roleId, request.phone);
        auditService.record(operator.getId(), "USER_CREATE", "APP_USER", username,
                Collections.singletonMap("roleCode", request.roleCode));
        return ApiResponse.success(Collections.singletonMap("created", true));
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以管理用户账号");
        return user;
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable long id, @Valid @RequestBody UpdateRequest request,
                                 Authentication authentication) {
        AuthenticatedUser operator = requireAdmin(authentication);
        Map<String, Object> target = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, r.role_code AS roleCode FROM app_user u JOIN app_role r ON r.id=u.role_id WHERE u.id=?", id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM app_role WHERE role_code=?", Long.class, request.roleCode);
        if (roleId == null) throw new IllegalArgumentException("角色不存在");
        boolean self = operator.getId() != null && operator.getId() == id;
        if (self && (!request.roleCode.equals(target.get("roleCode")) || request.enabled == null || !request.enabled)) {
            throw new IllegalArgumentException("不能停用自己的账号或修改自己的角色");
        }
        boolean resetPassword = request.password != null && !request.password.trim().isEmpty();
        jdbcTemplate.update("UPDATE app_user SET display_name=?, phone=?, role_id=?, enabled=? WHERE id=?",
                request.displayName.trim(), request.phone, roleId, request.enabled == null || request.enabled ? 1 : 0, id);
        if (resetPassword) {
            jdbcTemplate.update("UPDATE app_user SET password_hash=? WHERE id=?",
                    passwordEncoder.encode(request.password), id);
        }
        Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("roleCode", request.roleCode);
        detail.put("enabled", request.enabled == null || request.enabled);
        if (resetPassword) detail.put("passwordReset", true);
        auditService.record(operator.getId(), "USER_UPDATE", "APP_USER", String.valueOf(target.get("username")), detail);
        return ApiResponse.success(Collections.singletonMap("updated", true));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser operator = requireAdmin(authentication);
        if (operator.getId() != null && operator.getId() == id) throw new IllegalArgumentException("不能删除当前登录的账号");
        Map<String, Object> target = jdbcTemplate.queryForList(
                "SELECT u.id, u.username FROM app_user u WHERE u.id=?", id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String[] references = {
                "SELECT COUNT(*) FROM inspection_record WHERE user_id=?",
                "SELECT COUNT(*) FROM inspection_task WHERE assigned_user_id=?",
                "SELECT COUNT(*) FROM maintenance_record WHERE created_by=?",
                "SELECT COUNT(*) FROM rectification_order WHERE resolved_by=?",
                "SELECT COUNT(*) FROM inspection_plan WHERE assigned_user_id=?",
        };
        for (String sql : references) {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
            if (count != null && count > 0) throw new IllegalArgumentException("该账号已有业务记录，不能删除，请改用停用");
        }
        jdbcTemplate.update("DELETE FROM inspection_session WHERE user_id=?", id);
        jdbcTemplate.update("DELETE FROM app_user WHERE id=?", id);
        auditService.record(operator.getId(), "USER_DELETE", "APP_USER", String.valueOf(target.get("username")), null);
        return ApiResponse.success(Collections.singletonMap("deleted", true));
    }

    public static class CreateRequest {
        @NotBlank(message = "请输入账号") @Size(min = 3, max = 64, message = "账号长度需在3到64之间") public String username;
        @NotBlank(message = "请输入姓名") public String displayName;
        @NotBlank(message = "请输入初始密码") @Size(min = 8, max = 64, message = "密码至少8位") public String password;
        @NotBlank(message = "请选择角色") public String roleCode;
        public String phone;
    }

    public static class UpdateRequest {
        @NotBlank(message = "请输入姓名") public String displayName;
        @NotBlank(message = "请选择角色") public String roleCode;
        public String phone;
        public Boolean enabled;
        @Size(min = 8, max = 64, message = "密码至少8位") public String password;
    }
}
