package com.campusfire.audit;

import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit-logs")
public class AuditController {
    private final JdbcTemplate jdbcTemplate;
    public AuditController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate=jdbcTemplate; }
    @GetMapping
    public ApiResponse<List<Map<String,Object>>> list(Authentication authentication,
                                                       @RequestParam(required=false) String action,
                                                       @RequestParam(defaultValue="50") int limit) {
        if (!"ADMIN".equals(role(authentication))) throw new AccessDeniedException("仅管理员可查看审计日志");
        int safeLimit=Math.min(Math.max(limit,1),200);
        String sql="SELECT l.id,l.operator_user_id,u.username,u.display_name,l.action_code,l.target_type,l.target_id,l.detail_json,l.created_at FROM audit_log l LEFT JOIN app_user u ON u.id=l.operator_user_id WHERE (? IS NULL OR l.action_code=?) ORDER BY l.id DESC LIMIT "+safeLimit;
        return ApiResponse.success(jdbcTemplate.queryForList(sql, action, action));
    }
    private String role(Authentication a) { return a.getAuthorities().stream().findFirst().map(x->x.getAuthority().replace("ROLE_","")).orElse(""); }
}

