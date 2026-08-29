package com.campusfire.inspection;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inspection-tasks")
public class InspectionTaskAdminController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AuditService auditService;

    public InspectionTaskAdminController(JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "200") int limit,
                                                 Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以查看全部巡检任务");
        int max = Math.max(1, Math.min(limit, 500));
        String pattern = keyword == null || keyword.trim().isEmpty() ? null : "%" + keyword.trim() + "%";
        String sql = "SELECT t.id,t.due_date,t.status,t.completed_at,t.assigned_user_id, " +
                "u.display_name AS assignee_name,f.facility_no,f.name,f.campus,f.building,f.floor,f.area, " +
                "ft.type_name AS facility_type_name,p.plan_name " +
                "FROM inspection_task t " +
                "JOIN facility f ON f.id=t.facility_id " +
                "LEFT JOIN facility_type ft ON ft.id=f.facility_type_id " +
                "LEFT JOIN inspection_plan p ON p.id=t.plan_id " +
                "LEFT JOIN app_user u ON u.id=t.assigned_user_id " +
                "WHERE (? IS NULL OR t.status=?) " +
                "AND (? IS NULL OR f.facility_no LIKE ? OR f.name LIKE ? OR f.building LIKE ? OR u.display_name LIKE ?) " +
                "ORDER BY CASE WHEN t.status IN ('PENDING','IN_PROGRESS') AND t.due_date<CURDATE() THEN 0 " +
                "WHEN t.status='PENDING' THEN 1 WHEN t.status='IN_PROGRESS' THEN 2 ELSE 3 END, t.due_date, t.id DESC LIMIT " + max;
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(sql,
                status, status, pattern, pattern, pattern, pattern, pattern);
        Map<String, Object> data = new HashMap<>();
        data.put("tasks", tasks);
        data.put("summary", jdbcTemplate.queryForList(
                "SELECT status,COUNT(*) AS count FROM inspection_task GROUP BY status"));
        data.put("overdueCount", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inspection_task WHERE status IN ('PENDING','IN_PROGRESS') AND due_date<CURDATE()",
                Integer.class));
        return ApiResponse.success(data);
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable long id, @Valid @RequestBody UpdateRequest request,
                                 Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        String status = jdbcTemplate.queryForObject("SELECT status FROM inspection_task WHERE id=?", String.class, id);
        if (status == null) throw new IllegalArgumentException("巡检任务不存在");
        if (!"PENDING".equals(status)) throw new IllegalArgumentException("任务已开始或已完成，不能再调整");
        jdbcTemplate.update("UPDATE inspection_task SET assigned_user_id=?, due_date=? WHERE id=? AND status='PENDING'",
                request.assignedUserId, request.dueDate, id);
        auditService.record(user.getId(), "INSPECTION_TASK_UPDATE", "INSPECTION_TASK", String.valueOf(id),
                Collections.singletonMap("dueDate", String.valueOf(request.dueDate)));
        return ApiResponse.success(Collections.singletonMap("updated", true));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        String status = jdbcTemplate.queryForObject("SELECT status FROM inspection_task WHERE id=?", String.class, id);
        if (status == null) throw new IllegalArgumentException("巡检任务不存在");
        if (!"PENDING".equals(status)) throw new IllegalArgumentException("任务已开始或已完成，不能取消");
        int deleted = jdbcTemplate.update("DELETE FROM inspection_task WHERE id=? AND status='PENDING'", id);
        if (deleted == 0) throw new IllegalArgumentException("任务已开始或已完成，不能取消");
        auditService.record(user.getId(), "INSPECTION_TASK_DELETE", "INSPECTION_TASK", String.valueOf(id), null);
        return ApiResponse.success(Collections.singletonMap("deleted", true));
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以管理巡检任务");
        return user;
    }

    public static class UpdateRequest {
        public Long assignedUserId;
        @NotNull public LocalDate dueDate;
    }
}
