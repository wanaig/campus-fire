package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import com.campusfire.common.pdf.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/qr-codes")
public class QrCodeController {
    private static final String DEFAULT_SCHOOL = PdfService.BRAND_TITLE;
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final FacilityService facilityService;
    private final AuditService auditService;
    private final PdfService pdfService;

    public QrCodeController(JdbcTemplate jdbcTemplate, AuthService authService,
                            FacilityService facilityService, AuditService auditService,
                            PdfService pdfService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.facilityService = facilityService;
        this.auditService = auditService;
        this.pdfService = pdfService;
    }

    /** 标签 PDF：按筛选条件输出二维码介绍标签，每个二维码一张横向 A4。 */
    @GetMapping("/labels.pdf")
    public ResponseEntity<byte[]> labelsPdf(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) List<Long> ids,
                                            @RequestParam(required = false) String keyword,
                                            Authentication authentication) throws Exception {
        requireAdmin(authentication);
        if ("DELETED".equals(status)) throw new IllegalArgumentException("已删除的二维码不能下载标签 PDF，请先恢复");
        List<Map<String, Object>> rows;
        if (ids != null && !ids.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            Object[] args = new Object[ids.size()];
            for (int i = 0; i < ids.size(); i++) args[i] = ids.get(i);
            rows = jdbcTemplate.queryForList("SELECT q.token,q.serial_no,q.school,q.campus,q.building,q.floor,q.location_no,q.location_label,f.facility_no,f.name AS facility_name " +
                    "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id " +
                    "WHERE q.id IN (" + placeholders + ") AND q.status<>'DELETED' ORDER BY q.serial_no DESC", args);
        } else {
            String like = keyword == null ? "" : "%" + keyword.trim() + "%";
            String statusSql = status == null || status.trim().isEmpty() ? "q.status<>'DELETED'" : "q.status=?";
            String sql = "SELECT q.token,q.serial_no,q.school,q.campus,q.building,q.floor,q.location_no,q.location_label,f.facility_no,f.name AS facility_name " +
                    "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id " +
                    "WHERE " + statusSql + " AND (?='' OR q.token LIKE ? OR f.facility_no LIKE ? OR f.name LIKE ? OR q.school LIKE ? OR q.campus LIKE ? OR q.building LIKE ? OR q.floor LIKE ? OR q.location_label LIKE ?) " +
                    "ORDER BY q.serial_no DESC";
            List<Object> args = new ArrayList<>();
            if (status != null && !status.trim().isEmpty()) args.add(status);
            for (int i = 0; i < 9; i++) args.add(like);
            rows = jdbcTemplate.queryForList(sql, args.toArray());
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("没有符合条件的二维码");
        List<PdfService.LabelData> labels = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String serial = String.valueOf(row.get("serial_no"));
            String location = joinLocation(row);
            String facility = row.get("facility_no") == null ? "" : String.valueOf(row.get("facility_name")) + "（" + row.get("facility_no") + "）";
            String displayCode = labelCode(row);
            labels.add(new PdfService.LabelData(String.valueOf(row.get("token")), serial, location, facility, displayCode));
        }
        byte[] pdf = pdfService.labelsPdf(labels);
        auditService.record(authentication != null ? authService.loadCurrentUser(authentication.getName()).getId() : null,
                "QRCODE_LABEL_PDF", "FACILITY_QR_CODE", "labels", Collections.singletonMap("count", labels.size()));
        return pdfResponse(pdf, "qr-labels.pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String joinLocation(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        for (String key : new String[]{"school", "campus", "building", "floor"}) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isEmpty()) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @PostMapping("/batch")
    @Transactional
    public ApiResponse<List<Map<String, Object>>> batch(@Valid @RequestBody BatchRequest request,
                                                        Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        Integer base = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(serial_no),0) FROM facility_qr_code", Integer.class);
        int next = base == null ? 0 : base;
        List<Map<String, Object>> created = new ArrayList<>();
        int total = 0;
        int nextSerial = next;
        for (BatchRequest.Group group : request.groups == null ? Collections.<BatchRequest.Group>emptyList() : request.groups) {
            if (group == null) continue;
            int[] result = generateGroup(group, nextSerial, created);
            total += result[0];
            nextSerial = result[1];
        }
        if ((request.groups == null || request.groups.isEmpty()) && request.count != null && request.count > 0) {
            int count = Math.min(request.count, 500);
            for (int i = 1; i <= count; i++) {
                String token = UUID.randomUUID().toString().replace("-", "");
                jdbcTemplate.update("INSERT INTO facility_qr_code(token,serial_no,school) VALUES(?,?,?)", token, ++nextSerial, DEFAULT_SCHOOL);
                created.add(row(token, nextSerial, DEFAULT_SCHOOL, null, null, null, null, null));
            }
            total += count;
        }
        auditService.record(user.getId(), "QRCODE_BATCH_CREATE", "FACILITY_QR_CODE", "batch",
                Collections.singletonMap("count", total));
        return ApiResponse.success(created);
    }

    private int[] generateGroup(BatchRequest.Group group, int startSerial, List<Map<String, Object>> created) {
        String school = value(group.school);
        if (school.isEmpty()) school = DEFAULT_SCHOOL;
        String campus = group.campus == null ? "" : group.campus.trim();
        String building = group.building == null ? "" : group.building.trim();
        String floor = group.floor == null ? "" : group.floor.trim();
        if (campus.isEmpty() || building.isEmpty() || floor.isEmpty()) {
            throw new IllegalArgumentException("学校、校区、楼栋和楼层必须填写完整");
        }
        List<String> labels = normalizeLabels(group.labels);
        int count;
        if (labels.isEmpty()) {
            if (group.count == null || group.count < 1) throw new IllegalArgumentException("请填写二维码数量或自定义标签名称");
            count = Math.min(group.count, 500);
        } else {
            count = labels.size();
            ensureLabelsAvailable(school, campus, building, floor, labels, null);
        }
        Integer currentMax = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(location_no),0) FROM facility_qr_code WHERE school=? AND campus=? AND building=? AND floor=?",
                Integer.class, school, campus, building, floor);
        int floorBase = group.startNo == null ? (currentMax == null ? 1 : currentMax + 1) : Math.max(1, group.startNo);
        int serial = startSerial;
        for (int i = 0; i < count; i++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            int locationNo = floorBase + i;
            String locationLabel = labels.isEmpty() ? null : labels.get(i);
            jdbcTemplate.update("INSERT INTO facility_qr_code(token,serial_no,school,campus,building,floor,location_no,location_label) VALUES(?,?,?,?,?,?,?,?)",
                    token, ++serial, school, campus, building, floor, locationNo, locationLabel);
            created.add(row(token, serial, school, campus, building, floor, locationNo, locationLabel));
        }
        return new int[]{count, serial};
    }

    private List<String> normalizeLabels(List<String> labels) {
        if (labels == null) return Collections.emptyList();
        List<String> normalized = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String label : labels) {
            String value = label == null ? "" : label.trim();
            if (value.isEmpty()) continue;
            if (value.length() > 120) throw new IllegalArgumentException("二维码名称不能超过 120 个字符");
            if (!unique.add(value.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("同一楼层的二维码名称不能重复：" + value);
            normalized.add(value);
        }
        if (normalized.size() > 500) throw new IllegalArgumentException("每层最多设置 500 个二维码名称");
        return normalized;
    }

    private void ensureLabelsAvailable(String school, String campus, String building, String floor,
                                       List<String> labels, Long excludedId) {
        if (labels.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(labels.size(), "?"));
        String sql = "SELECT COUNT(*) FROM facility_qr_code WHERE school=? AND campus=? AND building=? AND floor=? " +
                "AND status<>'DELETED' AND location_label IN (" + placeholders + ")";
        List<Object> args = new ArrayList<>();
        args.add(school);
        args.add(campus);
        args.add(building);
        args.add(floor);
        args.addAll(labels);
        if (excludedId != null) {
            sql += " AND id<>?";
            args.add(excludedId);
        }
        Integer existing = jdbcTemplate.queryForObject(sql, Integer.class, args.toArray());
        if (existing != null && existing > 0) throw new IllegalArgumentException("该楼层已存在同名二维码，请更换名称");
    }

    private Map<String, Object> row(String token, int serial, String school, String campus, String building, String floor,
                                    Integer locationNo, String locationLabel) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("token", token);
        row.put("serialNo", serial);
        row.put("school", school);
        row.put("campus", campus);
        row.put("building", building);
        row.put("floor", floor);
        row.put("locationNo", locationNo);
        row.put("locationLabel", locationLabel);
        row.put("status", "UNCLAIMED");
        return row;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String keyword,
                                                       Authentication authentication) {
        requireAdmin(authentication);
        String like = keyword == null ? "" : "%" + keyword + "%";
        String statusSql = status == null ? "q.status<>'DELETED'" : "q.status=?";
        String sql = "SELECT q.id,q.token,q.serial_no,q.school,q.campus,q.building,q.floor,q.location_no,q.location_label,q.status,q.created_at,q.bound_at,q.deleted_at," +
                "q.facility_id,f.facility_no,f.name AS facility_name " +
                "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id " +
                "WHERE " + statusSql + " AND (?='' OR q.token LIKE ? OR f.facility_no LIKE ? OR f.name LIKE ? OR q.school LIKE ? OR q.campus LIKE ? OR q.building LIKE ? OR q.floor LIKE ? OR q.location_label LIKE ?) " +
                "ORDER BY q.serial_no DESC";
        List<Object> args = new ArrayList<>();
        if (status != null) args.add(status);
        for (int i = 0; i < 9; i++) args.add(like);
        return ApiResponse.success(jdbcTemplate.queryForList(sql, args.toArray()));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        Map<String, Object> qr = findQr(id);
        if ("DELETED".equals(String.valueOf(qr.get("status")))) throw new IllegalArgumentException("二维码已经删除");
        softDelete(qr);
        auditService.record(user.getId(), "QRCODE_DELETE", "FACILITY_QR_CODE", String.valueOf(id), qrDetails(qr));
        return ApiResponse.success(Collections.singletonMap("deleted", true));
    }

    @PutMapping("/{id}/label")
    @Transactional
    public ApiResponse<Map<String, Object>> updateLabel(@PathVariable long id,
                                                         @Valid @RequestBody LabelRequest request,
                                                         Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        Map<String, Object> qr = findQr(id);
        if ("DELETED".equals(String.valueOf(qr.get("status")))) throw new IllegalArgumentException("请先恢复已删除的二维码再修改名称");
        String label = request.label.trim();
        ensureLabelsAvailable(value(qr.get("school")), value(qr.get("campus")), value(qr.get("building")),
                value(qr.get("floor")), Collections.singletonList(label), id);
        jdbcTemplate.update("UPDATE facility_qr_code SET location_label=? WHERE id=?", label, id);
        Map<String, Object> details = qrDetails(qr);
        details.put("newLocationLabel", label);
        auditService.record(user.getId(), "QRCODE_LABEL_UPDATE", "FACILITY_QR_CODE", String.valueOf(id), details);
        return ApiResponse.success(jdbcTemplate.queryForMap(
                "SELECT id,token,serial_no,school,campus,building,floor,location_no,location_label,status FROM facility_qr_code WHERE id=?", id));
    }

    @PostMapping("/batch-delete")
    @Transactional
    public ApiResponse<Map<String, Object>> batchDelete(@Valid @RequestBody BatchDeleteRequest request,
                                                         Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        if (request.ids == null || request.ids.isEmpty()) throw new IllegalArgumentException("请至少选择一个二维码");
        if (request.ids.size() > 500) throw new IllegalArgumentException("每次最多删除 500 个二维码");
        int deleted = 0;
        for (Long id : request.ids) {
            if (id == null) continue;
            Map<String, Object> qr = findQr(id);
            if ("DELETED".equals(String.valueOf(qr.get("status")))) continue;
            softDelete(qr);
            deleted++;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ids", request.ids);
        details.put("count", deleted);
        auditService.record(user.getId(), "QRCODE_BATCH_DELETE", "FACILITY_QR_CODE", "batch", details);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/restore")
    @Transactional
    public ApiResponse<Map<String, Object>> restore(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        Map<String, Object> qr = findQr(id);
        if (!"DELETED".equals(String.valueOf(qr.get("status")))) throw new IllegalArgumentException("只有已删除的二维码可以恢复");
        jdbcTemplate.update("UPDATE facility_qr_code SET status='UNCLAIMED',deleted_at=NULL WHERE id=?", id);
        auditService.record(user.getId(), "QRCODE_RESTORE", "FACILITY_QR_CODE", String.valueOf(id), qrDetails(qr));
        return ApiResponse.success(jdbcTemplate.queryForMap(
                "SELECT id,token,status,school,campus,building,floor,location_no,location_label FROM facility_qr_code WHERE id=?", id));
    }

    private void softDelete(Map<String, Object> qr) {
        Object facilityId = qr.get("facility_id");
        if (facilityId instanceof Number) {
            jdbcTemplate.update("UPDATE facility SET qr_token=? WHERE id=?",
                    "DELETED-" + UUID.randomUUID().toString().replace("-", ""), ((Number) facilityId).longValue());
        }
        jdbcTemplate.update("UPDATE facility_qr_code SET status='DELETED',facility_id=NULL,bound_at=NULL,deleted_at=NOW() WHERE id=?",
                qr.get("id"));
    }

    /** 重置为未绑定：仅解除与设施的绑定，码值保持不变，采集员可重新扫码采集。 */
    @PostMapping("/{id}/unbind")
    @Transactional
    public ApiResponse<Map<String, Object>> unbind(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        Map<String, Object> qr = findQr(id);
        if ("DELETED".equals(String.valueOf(qr.get("status")))) throw new IllegalArgumentException("请先恢复已删除的二维码再重置");
        if (!(qr.get("facility_id") instanceof Number)) throw new IllegalArgumentException("该二维码未绑定设施，无需重置");
        Long facilityId = ((Number) qr.get("facility_id")).longValue();
        jdbcTemplate.update("UPDATE facility SET qr_token=? WHERE id=?",
                "UNBOUND-" + UUID.randomUUID().toString().replace("-", ""), facilityId);
        jdbcTemplate.update("UPDATE facility_qr_code SET status='UNCLAIMED',facility_id=NULL,bound_at=NULL WHERE id=?", id);
        Map<String, Object> details = qrDetails(qr);
        details.put("unboundFacilityId", facilityId);
        auditService.record(user.getId(), "QRCODE_UNBIND", "FACILITY_QR_CODE", String.valueOf(id), details);
        return ApiResponse.success(jdbcTemplate.queryForMap(
                "SELECT id,token,status,facility_id,bound_at FROM facility_qr_code WHERE id=?", id));
    }

    private Map<String, Object> findQr(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,token,serial_no,school,campus,building,floor,location_no,location_label,status,facility_id,deleted_at FROM facility_qr_code WHERE id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("二维码不存在");
        return rows.get(0);
    }

    private Map<String, Object> qrDetails(Map<String, Object> qr) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (String key : new String[]{"serial_no", "school", "campus", "building", "floor", "location_no", "location_label", "status", "facility_id"}) {
            details.put(key, qr.get(key));
        }
        return details;
    }

    @GetMapping("/resolve/{token}")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> pool = jdbcTemplate.queryForList(
                "SELECT serial_no,school,campus,building,floor,location_no,location_label,status,facility_id FROM facility_qr_code WHERE token=?", token);
        if (!pool.isEmpty()) {
            Map<String, Object> qr = pool.get(0);
            String status = String.valueOf(qr.get("status"));
            if ("DELETED".equals(status)) {
                result.put("type", "INVALID");
                result.put("message", "该二维码已被管理员删除，请更换有效标签");
                return ApiResponse.success(result);
            }
            if ("UNCLAIMED".equals(status)) {
                result.put("type", "BLANK");
                result.put("serialNo", qr.get("serial_no"));
                result.put("school", qr.get("school"));
                result.put("campus", qr.get("campus"));
                result.put("building", qr.get("building"));
                result.put("floor", qr.get("floor"));
                result.put("locationNo", qr.get("location_no"));
                result.put("locationLabel", qr.get("location_label"));
                result.put("labelCode", labelCode(qr));
                return ApiResponse.success(result);
            }
            if ("BOUND".equals(status) && qr.get("facility_id") != null) {
                FacilityDtos.Summary facility = facilityService.detail(((Number) qr.get("facility_id")).longValue());
                if (facility != null) {
                    result.put("type", "FACILITY");
                    result.put("facility", facility);
                    return ApiResponse.success(result);
                }
            }
            result.put("type", "INVALID");
            result.put("message", "该二维码已作废，请扫描有效的设施巡检码");
            return ApiResponse.success(result);
        }
        List<Map<String, Object>> legacy = jdbcTemplate.queryForList(
                "SELECT id FROM facility WHERE qr_token=?", token);
        if (!legacy.isEmpty()) {
            result.put("type", "FACILITY");
            result.put("facility", facilityService.detail(((Number) legacy.get(0).get("id")).longValue()));
            return ApiResponse.success(result);
        }
        result.put("type", "UNKNOWN");
        result.put("message", "该二维码不是本系统生成的设施巡检码，请扫描张贴在设备上的空白码");
        return ApiResponse.success(result);
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode())) throw new AccessDeniedException("仅管理员可以管理二维码");
        return user;
    }

    /** 采集员扫码建档用：优先使用管理员设置的名称，旧二维码继续使用楼栋-楼层-序号。 */
    private String labelCode(Map<String, Object> qr) {
        String customLabel = value(qr.get("location_label"));
        if (!customLabel.isEmpty()) return customLabel;
        String building = value(qr.get("building"));
        String floor = value(qr.get("floor"));
        Object serial = qr.get("serial_no");
        if (building.isEmpty() || floor.isEmpty() || serial == null) {
            return "NO." + (serial == null ? "?" : serial);
        }
        Integer rank = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)+1 FROM facility_qr_code WHERE school=? AND campus=? AND building=? AND floor=? " +
                        "AND status<>'DELETED' AND serial_no<?",
                Integer.class, qr.get("school"), qr.get("campus"), building, floor, serial);
        return "NO." + building + "-" + floor + "-" + String.format("%02d", rank == null ? 1 : rank);
    }

    public static class BatchRequest {
        @Min(value = 1, message = "每次至少生成 1 个")
        @Max(value = 500, message = "每次最多生成 500 个")
        public Integer count;
        public String campus;
        public String building;
        public String floor;
        public List<Group> groups;

        public static class Group {
            public String school;
            public String campus;
            public String building;
            public String floor;
            @Min(value = 1, message = "每层至少生成 1 个")
            @Max(value = 500, message = "每层最多生成 500 个")
            public Integer count;
            public Integer startNo;
            /** 自定义名称列表，例如：东、东楼梯口、西。为空时兼容旧版数量+序号生成。 */
            public List<String> labels;
        }
    }

    public static class LabelRequest {
        @NotBlank(message = "请输入二维码名称")
        @Size(max = 120, message = "二维码名称不能超过 120 个字符")
        public String label;
    }

    public static class BatchDeleteRequest {
        @NotNull(message = "请选择需要删除的二维码")
        public List<Long> ids;
    }
}
