package com.campusfire.common.setting;

import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.audit.AuditService;
import com.campusfire.common.api.ApiResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端系统设置：仅管理员可查看与修改。
 * 当前包含扫码巡检定位校验开关（INSPECTION_LOCATION_VALIDATION）。
 */
@RestController
@RequestMapping("/settings")
public class SystemSettingController {
    private final SystemSettingService settingService;
    private final AuthService authService;
    private final AuditService auditService;

    public SystemSettingController(SystemSettingService settingService, AuthService authService, AuditService auditService) {
        this.settingService = settingService;
        this.authService = authService;
        this.auditService = auditService;
    }

    /** 读取巡检相关设置：保安端扫码解析（公开接口）也会用到该开关值 */
    @GetMapping("/inspection")
    public ApiResponse<Map<String, Object>> inspection(Authentication authentication) {
        requireAdmin(authentication);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("locationValidationEnabled", settingService.isLocationValidationEnabled());
        return ApiResponse.success(data);
    }

    /** 修改定位校验开关：关闭后保安扫码巡检不再做定位距离校验 */
    @PutMapping("/inspection")
    public ApiResponse<Map<String, Object>> updateInspection(@RequestBody UpdateRequest r, Authentication authentication) {
        AuthenticatedUser admin = requireAdmin(authentication);
        settingService.setLocationValidationEnabled(Boolean.TRUE.equals(r.locationValidation), admin.getId());
        auditService.record(admin.getId(), "SYSTEM_SETTING_UPDATE", "SYSTEM_SETTING",
                SystemSettingService.KEY_INSPECTION_LOCATION_VALIDATION,
                java.util.Collections.singletonMap("locationValidation", Boolean.TRUE.equals(r.locationValidation)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("locationValidationEnabled", Boolean.TRUE.equals(r.locationValidation));
        return ApiResponse.success(data);
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以管理系统设置");
        return user;
    }

    public static class UpdateRequest {
        public Boolean locationValidation;
    }
}
