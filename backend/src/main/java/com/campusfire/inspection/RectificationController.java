package com.campusfire.inspection;

import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import com.campusfire.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/rectifications")
public class RectificationController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AuditService auditService;

    public RectificationController(JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       Authentication authentication) {
        String sql = "SELECT r.id,r.record_id,r.task_id,r.facility_id,f.facility_no,f.name,f.campus,f.building,f.floor,f.area,r.issue_summary,r.status,r.due_date,r.resolution_note,r.resolved_at,r.resolved_by,r.created_at FROM rectification_order r JOIN facility f ON f.id=r.facility_id WHERE (? IS NULL OR r.status=?) ORDER BY r.created_at DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, status, status));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<?> resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request,
                                  Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以关闭整改单");
        int updated = jdbcTemplate.update("UPDATE rectification_order SET status='RESOLVED',resolution_note=?,resolved_at=NOW(),resolved_by=? WHERE id=? AND status='OPEN'",
                request.resolutionNote, user.getId(), id);
        if (updated == 0) throw new IllegalArgumentException("整改单不存在或已经关闭");
        auditService.record(user.getId(), "RECTIFICATION_RESOLVE", "RECTIFICATION_ORDER", String.valueOf(id),
                Collections.singletonMap("resolutionNote", request.resolutionNote));
        return ApiResponse.success(Collections.singletonMap("resolved", true));
    }

    @PostMapping("/{id}/evidence")
    public ApiResponse<?> evidence(@PathVariable Long id, @RequestBody EvidenceRequest request, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode()) && !"COLLECTOR".equals(user.getRoleCode())) throw new AccessDeniedException("当前账号无整改凭证权限");
        jdbcTemplate.update("UPDATE rectification_order SET evidence_urls=? WHERE id=?", request.urls == null ? "[]" : request.urls.toString(), id);
        auditService.record(user.getId(), "RECTIFICATION_EVIDENCE", "RECTIFICATION_ORDER", String.valueOf(id), Collections.singletonMap("count", request.urls == null ? 0 : request.urls.size()));
        return ApiResponse.success(Collections.singletonMap("saved", true));
    }

    public static class ResolveRequest {
        @NotBlank public String resolutionNote;
    }
    public static class EvidenceRequest { public List<String> urls; }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest request, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以创建整改单");
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM facility WHERE id=?", Integer.class, request.facilityId);
        if (exists == null || exists == 0) throw new IllegalArgumentException("设施不存在");
        jdbcTemplate.update("INSERT INTO rectification_order(facility_id,issue_summary,due_date) VALUES(?,?,?)",
                request.facilityId, request.issueSummary.trim(), request.dueDate);
        auditService.record(user.getId(), "RECTIFICATION_CREATE", "RECTIFICATION_ORDER",
                String.valueOf(request.facilityId), Collections.singletonMap("issueSummary", request.issueSummary.trim()));
        return ApiResponse.success(Collections.singletonMap("created", true));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody CreateRequest request, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以编辑整改单");
        int updated = jdbcTemplate.update("UPDATE rectification_order SET issue_summary=?,due_date=? WHERE id=? AND status='OPEN'",
                request.issueSummary.trim(), request.dueDate, id);
        if (updated == 0) throw new IllegalArgumentException("整改单不存在或已经关闭，不能编辑");
        auditService.record(user.getId(), "RECTIFICATION_UPDATE", "RECTIFICATION_ORDER", String.valueOf(id),
                Collections.singletonMap("issueSummary", request.issueSummary.trim()));
        return ApiResponse.success(Collections.singletonMap("updated", true));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以删除整改单");
        int deleted = jdbcTemplate.update("DELETE FROM rectification_order WHERE id=? AND status='OPEN'", id);
        if (deleted == 0) throw new IllegalArgumentException("整改单不存在或已经关闭，不能删除");
        auditService.record(user.getId(), "RECTIFICATION_DELETE", "RECTIFICATION_ORDER", String.valueOf(id), null);
        return ApiResponse.success(Collections.singletonMap("deleted", true));
    }

    public static class CreateRequest {
        @javax.validation.constraints.NotNull public Long facilityId;
        @NotBlank(message = "请填写异常内容") public String issueSummary;
        public String dueDate;
    }
}
