package com.campusfire.inspection;

import com.campusfire.common.api.ApiResponse;
import com.campusfire.storage.PhotoStorageCleaner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.audit.AuditService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;

@RestController
@RequestMapping("/inspection/records")
public class InspectionRecordController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AuditService auditService;
    private final InspectionResultSupport resultSupport;
    private final PhotoStorageCleaner photoStorageCleaner;

    public InspectionRecordController(JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService,
                                      InspectionResultSupport resultSupport, PhotoStorageCleaner photoStorageCleaner) {
        this.jdbcTemplate = jdbcTemplate; this.authService = authService; this.auditService = auditService;
        this.resultSupport = resultSupport;
        this.photoStorageCleaner = photoStorageCleaner;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String facilityNo,
                                                       @RequestParam(required = false) String inspector) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT r.id,r.task_id,r.facility_id,r.submitted_at,r.results_json,r.note,r.photo_count,c.reason AS void_reason,f.facility_no,f.name,f.campus,f.building,f.floor,f.area,u.display_name AS inspector FROM inspection_record r LEFT JOIN (SELECT record_id,MAX(id) AS max_id FROM inspection_record_correction GROUP BY record_id) lc ON lc.record_id=r.id LEFT JOIN inspection_record_correction c ON c.id=lc.max_id JOIN facility f ON f.id=r.facility_id JOIN app_user u ON u.id=r.user_id WHERE (? IS NULL OR f.facility_no=?) AND (? IS NULL OR u.display_name LIKE CONCAT('%',?,'%')) ORDER BY r.submitted_at DESC",
                facilityNo, facilityNo, inspector, inspector);
        if (!rows.isEmpty()) attachStructuredResults(rows);
        return ApiResponse.success(rows);
    }

    /** 把 results_json 转成 [{code,name,status}]，部件名称优先按采集员建档登记的档案部件翻译 */
    private void attachStructuredResults(List<Map<String, Object>> rows) {
        Set<Long> facilityIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object facilityId = row.get("facility_id");
            if (facilityId != null) facilityIds.add(((Number) facilityId).longValue());
        }
        Map<Long, LinkedHashMap<String, String>> facilityLabels = resultSupport.loadFacilityLabels(facilityIds);
        for (Map<String, Object> row : rows) {
            Map<String, String> results = resultSupport.parseResults(row.get("results_json"));
            Object facilityId = row.get("facility_id");
            LinkedHashMap<String, String> labels = facilityLabels.getOrDefault(
                    facilityId == null ? null : ((Number) facilityId).longValue(), new LinkedHashMap<>());
            List<Map<String, Object>> structured = new ArrayList<>();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                if (results.containsKey(entry.getKey())) {
                    structured.add(structuredItem(entry.getKey(), entry.getValue(), results.get(entry.getKey())));
                }
            }
            for (Map.Entry<String, String> entry : results.entrySet()) {
                if (!labels.containsKey(entry.getKey())) {
                    structured.add(structuredItem(entry.getKey(), entry.getKey(), entry.getValue()));
                }
            }
            row.put("results", structured);
        }
    }

    /** 管理端查看某条巡检记录的现场照片清单，照片文件经 /inspection/photos/{photoId}/file 拉取 */
    @GetMapping("/{id}/photos")
    public ApiResponse<List<Map<String, Object>>> photos(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id,p.client_captured_at,p.received_at FROM inspection_photo p JOIN inspection_record r ON r.session_id=p.session_id WHERE r.id=? ORDER BY p.received_at,p.id", id);
        List<Map<String, Object>> photos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> photo = new LinkedHashMap<>();
            photo.put("photoId", String.valueOf(row.get("id")));
            photo.put("capturedAt", row.get("client_captured_at"));
            photos.add(photo);
        }
        return ApiResponse.success(photos);
    }

    private Map<String, Object> structuredItem(String code, String name, String status) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("status", status);
        return item;
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

    /**
     * 物理删除巡检记录及其会话链数据（照片、草稿、更正、风险标记、整改单、会话），供管理端清理测试数据。
     * 顺序遵循外键依赖：照片→更正→风险→整改→记录→草稿→会话；照片对象/文件尽力清理。
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<?> delete(@PathVariable String id, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以删除巡检记录");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT r.id,r.session_id,f.facility_no FROM inspection_record r JOIN facility f ON f.id=r.facility_id WHERE r.id=?", id);
        if (records.isEmpty()) throw new IllegalArgumentException("巡检记录不存在");
        String sessionId = String.valueOf(records.get(0).get("session_id"));
        List<Map<String, Object>> photos = jdbcTemplate.queryForList(
                "SELECT id,storage_path,original_storage_path,storage_backend FROM inspection_photo WHERE session_id=?", sessionId);
        jdbcTemplate.update("DELETE FROM inspection_photo WHERE session_id=?", sessionId);
        jdbcTemplate.update("DELETE FROM inspection_record_correction WHERE record_id=?", id);
        jdbcTemplate.update("DELETE FROM inspection_risk_flag WHERE session_id=?", sessionId);
        jdbcTemplate.update("DELETE FROM rectification_order WHERE record_id=?", id);
        jdbcTemplate.update("DELETE FROM inspection_record WHERE id=?", id);
        jdbcTemplate.update("DELETE FROM inspection_draft WHERE session_id=?", sessionId);
        jdbcTemplate.update("DELETE FROM inspection_session WHERE id=?", sessionId);
        photoStorageCleaner.deleteQuietly(photos);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sessionId", sessionId);
        details.put("facilityNo", records.get(0).get("facility_no"));
        details.put("photosDeleted", photos.size());
        auditService.record(user.getId(), "INSPECTION_RECORD_DELETE", "INSPECTION_RECORD", id, details);
        return ApiResponse.success(Collections.singletonMap("deleted", true));
    }

    public static class CorrectionRequest { public String reason; public String correctionNote; }
}
