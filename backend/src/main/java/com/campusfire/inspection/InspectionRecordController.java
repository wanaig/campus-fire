package com.campusfire.inspection;

import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.audit.AuditService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@RestController
@RequestMapping("/inspection/records")
public class InspectionRecordController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AuditService auditService;

    public InspectionRecordController(JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate; this.authService = authService; this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String facilityNo) {
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT r.id,r.task_id,r.submitted_at,r.results_json,r.note,r.photo_count,c.reason AS void_reason,f.facility_no,f.name,f.campus,f.building,f.floor,f.area,u.display_name AS inspector FROM inspection_record r LEFT JOIN (SELECT record_id,MAX(id) AS max_id FROM inspection_record_correction GROUP BY record_id) lc ON lc.record_id=r.id LEFT JOIN inspection_record_correction c ON c.id=lc.max_id JOIN facility f ON f.id=(SELECT facility_id FROM inspection_task WHERE id=r.task_id) JOIN app_user u ON u.id=r.user_id WHERE (? IS NULL OR f.facility_no=?) ORDER BY r.submitted_at DESC",
                facilityNo, facilityNo));
    }

    @PostMapping("/{id}/void")
    public ApiResponse<?> voidRecord(@PathVariable String id, @RequestBody CorrectionRequest request, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以作废正式巡检记录");
        if (request.reason == null || request.reason.trim().isEmpty()) throw new IllegalArgumentException("请填写作废原因");
        int updated = jdbcTemplate.update("INSERT INTO inspection_record_correction(record_id,reason,correction_note,operator_user_id) VALUES(?,?,?,?)", id, request.reason.trim(), request.correctionNote, user.getId());
        if (updated == 0) throw new IllegalArgumentException("巡检记录不存在");
        auditService.record(user.getId(), "INSPECTION_RECORD_VOID", "INSPECTION_RECORD", id, Collections.singletonMap("reason", request.reason));
        return ApiResponse.success(Collections.singletonMap("voided", true));
    }

    public static class CorrectionRequest { public String reason; public String correctionNote; }
}
