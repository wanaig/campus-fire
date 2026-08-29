package com.campusfire.dashboard;

import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;

    public DashboardController(JdbcTemplate jdbcTemplate, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
    }

    @GetMapping("/overview")
    public ApiResponse<?> overview(Authentication authentication,
                                   @RequestParam(defaultValue = "30") int trendDays) {
        requireAdmin(authentication);
        int days = Math.max(7, Math.min(trendDays, 90));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", LocalDate.now());
        data.put("facilitySummary", jdbcTemplate.queryForList(
                "SELECT ft.type_code AS facilityType,ft.type_name AS facilityTypeName,COUNT(f.id) AS totalCount,SUM(CASE WHEN f.lifecycle_status='IN_USE' THEN 1 ELSE 0 END) AS inUseCount,SUM(CASE WHEN f.lifecycle_status<>'IN_USE' THEN 1 ELSE 0 END) AS inactiveCount FROM facility_type ft LEFT JOIN facility f ON f.facility_type_id=ft.id GROUP BY ft.id,ft.type_code,ft.type_name ORDER BY ft.id"));
        data.put("facilityTotal", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM facility", Integer.class));
        data.put("taskSummary", jdbcTemplate.queryForList(
                "SELECT status,COUNT(*) AS count FROM inspection_task GROUP BY status ORDER BY status"));
        data.put("rectificationSummary", jdbcTemplate.queryForList(
                "SELECT status,COUNT(*) AS count FROM rectification_order GROUP BY status ORDER BY status"));
        data.put("overdueTaskCount", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inspection_task WHERE status IN ('PENDING','IN_PROGRESS') AND due_date<CURDATE()", Integer.class));
        data.put("maintenanceDueFacilities", jdbcTemplate.queryForList(
                "SELECT id,facility_no,name,campus,building,floor,area,next_maintenance_at FROM facility WHERE next_maintenance_at IS NOT NULL AND next_maintenance_at<=DATE_ADD(NOW(),INTERVAL 30 DAY) ORDER BY next_maintenance_at LIMIT 100"));
        data.put("inspectionTrend", jdbcTemplate.queryForList(
                "SELECT DATE(submitted_at) AS date,COUNT(*) AS completedCount FROM inspection_record WHERE submitted_at>=DATE_SUB(CURDATE(),INTERVAL ? DAY) GROUP BY DATE(submitted_at) ORDER BY date", days));
        return ApiResponse.success(data);
    }

    @GetMapping("/rectifications")
    public ApiResponse<List<Map<String, Object>>> rectifications(Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT r.id,r.status,r.issue_summary,r.due_date,r.created_at,f.facility_no,f.name,f.campus,f.building,f.floor,f.area FROM rectification_order r JOIN facility f ON f.id=r.facility_id ORDER BY CASE WHEN r.status='OPEN' THEN 0 ELSE 1 END,r.due_date,r.created_at DESC"));
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以查看数据看板");
        return user;
    }
}
