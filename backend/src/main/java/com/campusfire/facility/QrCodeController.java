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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 标签 PDF：按筛选条件输出二维码标签（每页 9 张）。 */
    @GetMapping("/labels.pdf")
    public ResponseEntity<byte[]> labelsPdf(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) List<Long> ids,
                                            Authentication authentication) throws Exception {
        requireAdmin(authentication);
        if ("DELETED".equals(status)) throw new IllegalArgumentException("已删除的二维码不能下载标签 PDF，请先恢复");
        List<Map<String, Object>> rows;
        if (ids != null && !ids.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            Object[] args = new Object[ids.size()];
            for (int i = 0; i < ids.size(); i++) args[i] = ids.get(i);
            rows = jdbcTemplate.queryForList("SELECT q.token,q.serial_no,q.school,q.campus,q.building,q.floor,q.location_no,f.facility_no,f.name AS facility_name " +
                    "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id " +
                    "WHERE q.id IN (" + placeholders + ") AND q.status<>'DELETED' ORDER BY q.serial_no", args);
        } else {
            rows = jdbcTemplate.queryForList("SELECT q.token,q.serial_no,q.school,q.campus,q.building,q.floor,q.location_no,f.facility_no,f.name AS facility_name " +
                    "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id " +
                    "WHERE q.status<>'DELETED' AND (? IS NULL OR q.status=?) ORDER BY q.serial_no", status, status);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("没有符合条件的二维码");
        List<PdfService.LabelData> labels = new ArrayList<>();
        Map<String, Integer> floorCounters = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String serial = String.valueOf(row.get("serial_no"));
            String location = joinLocation(row);
            String facility = row.get("facility_no") == null ? "" : String.valueOf(row.get("facility_name")) + "（" + row.get("facility_no") + "）";
            String displayCode = labelDisplayCode(row, serial, floorCounters);
            labels.add(new PdfService.LabelData(String.valueOf(row.get("token")), serial, location, facility, displayCode));
        }
        byte[] pdf = pdfService.labelsPdf(labels);
        auditService.record(authentication != null ? authService.loadCurrentUser(authentication.getName()).getId() : null,
                "QRCODE_LABEL_PDF", "FACILITY_QR_CODE", "labels", Collections.singletonMap("count", labels.size()));
        return pdfResponse(pdf, "qr-labels.pdf");
    }

    /** 介绍页 PDF：单页，可选附带入口码。 */
    @GetMapping("/poster.pdf")
    public ResponseEntity<byte[]> posterPdf(@RequestParam(required = false) String entryToken,
                                            Authentication authentication) throws Exception {
        requireAdmin(authentication);
        byte[] pdf = pdfService.posterPdf(entryToken);
        auditService.record(authentication != null ? authService.loadCurrentUser(authentication.getName()).getId() : null,
                "QRCODE_POSTER_PDF", "FACILITY_QR_CODE", "poster", null);
        return pdfResponse(pdf, "platform-poster.pdf");
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

    /** 标签编号：同一「校区+楼栋+楼层」内按打印顺序从 01 起连续编号，每次打印都重新从 01 开始 */
    private String labelDisplayCode(Map<String, Object> row, String serial, Map<String, Integer> floorCounters) {
        String building = value(row.get("building"));
        String floor = value(row.get("floor"));
        if (!building.isEmpty() && !floor.isEmpty()) {
            String key = value(row.get("school")) + "|" + value(row.get("campus")) + "|" + building + "|" + floor;
            int position = floorCounters.merge(key, 1, Integer::sum);
            return "NO." + building + "-" + floor + "-" + String.format("%02d", position);
        }
        try {
            return "NO." + String.format("%03d", Integer.parseInt(serial));
        } catch (NumberFormatException ignored) {
            return "NO." + serial;
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @PostMapping("/batch")
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
                created.add(row(token, nextSerial, DEFAULT_SCHOOL, null, null, null, null));
            }
            total += count;
        }
        auditService.record(user.getId(), "QRCODE_BATCH_CREATE", "FACILITY_QR_CODE", "batch",
                Collections.singletonMap("count", total));
        return ApiResponse.success(created);
    }

    private int[] generateGroup(BatchRequest.Group group, int startSerial, List<Map<String, Object>> created) {
        int count = Math.max(1, Math.min(group.count, 500));
        String school = value(group.school);
        if (school.isEmpty()) school = DEFAULT_SCHOOL;
        String campus = group.campus == null ? "" : group.campus.trim();
        String building = group.building == null ? "" : group.building.trim();
        String floor = group.floor == null ? "" : group.floor.trim();
        if (campus.isEmpty() || building.isEmpty() || floor.isEmpty()) {
            throw new IllegalArgumentException("学校、校区、楼栋和楼层必须填写完整");
        }
        Integer currentMax = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(location_no),0) FROM facility_qr_code WHERE school=? AND campus=? AND building=? AND floor=?",
                Integer.class, school, campus, building, floor);
        int floorBase = group.startNo == null ? (currentMax == null ? 1 : currentMax + 1) : Math.max(1, group.startNo);
        int serial = startSerial;
        for (int i = 0; i < count; i++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            int locationNo = floorBase + i;
            jdbcTemplate.update("INSERT INTO facility_qr_code(token,serial_no,school,campus,building,floor,location_no) VALUES(?,?,?,?,?,?,?)",
                    token, ++serial, school, campus, building, floor, locationNo);
            created.add(row(token, serial, school, campus, building, floor, locationNo));
        }
        return new int[]{count, serial};
    }

    private Map<String, Object> row(String token, int serial, String school, String campus, String building, String floor, Integer locationNo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("token", token);
        row.put("serialNo", serial);
        row.put("school", school);
        row.put("campus", campus);
        row.put("building", building);
        row.put("floor", floor);
        row.put("locationNo", locationNo);
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
        String sql = "SELECT q.id,q.token,q.serial_no,q.school,q.campus,q.building,q.floor,q.location_no,q.status,q.created_at,q.bound_at,q.deleted_at," +
                "q.facility_id,f.facility_no,f.name AS facility_name " +
                "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id " +
                "WHERE " + statusSql + " AND (?='' OR q.token LIKE ? OR f.facility_no LIKE ? OR f.name LIKE ? OR q.school LIKE ? OR q.campus LIKE ? OR q.building LIKE ? OR q.floor LIKE ?) " +
                "ORDER BY q.serial_no DESC";
        List<Object> args = new ArrayList<>();
        if (status != null) args.add(status);
        for (int i = 0; i < 8; i++) args.add(like);
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
                "SELECT id,token,status,school,campus,building,floor,location_no FROM facility_qr_code WHERE id=?", id));
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

    @PostMapping("/{id}/rebind")
    @Transactional
    public ApiResponse<Map<String, Object>> rebind(@PathVariable long id,
                                                    @Valid @RequestBody RebindRequest request,
                                                    Authentication authentication) {
        AuthenticatedUser user = requireAdmin(authentication);
        Map<String, Object> qr = findQr(id);
        if ("DELETED".equals(String.valueOf(qr.get("status")))) throw new IllegalArgumentException("请先恢复已删除的二维码再重新绑定");
        List<Map<String, Object>> targetRows = jdbcTemplate.queryForList(
                "SELECT id,facility_no,name,qr_token FROM facility WHERE id=?", request.facilityId);
        if (targetRows.isEmpty()) throw new IllegalArgumentException("目标设施不存在");
        Map<String, Object> target = targetRows.get(0);
        Long oldFacilityId = qr.get("facility_id") instanceof Number ? ((Number) qr.get("facility_id")).longValue() : null;

        if (oldFacilityId != null && !oldFacilityId.equals(request.facilityId)) {
            jdbcTemplate.update("UPDATE facility SET qr_token=? WHERE id=?",
                    "UNBOUND-" + UUID.randomUUID().toString().replace("-", ""), oldFacilityId);
        }
        jdbcTemplate.update("UPDATE facility_qr_code SET status='UNCLAIMED',facility_id=NULL,bound_at=NULL,deleted_at=NULL " +
                "WHERE facility_id=? AND id<>?", request.facilityId, id);
        jdbcTemplate.update("UPDATE facility SET qr_token=? WHERE id=?", qr.get("token"), request.facilityId);
        jdbcTemplate.update("UPDATE facility_qr_code SET status='BOUND',facility_id=?,bound_at=NOW() WHERE id=?",
                request.facilityId, id);

        Map<String, Object> details = qrDetails(qr);
        details.put("oldFacilityId", oldFacilityId);
        details.put("newFacilityId", request.facilityId);
        details.put("newFacilityNo", target.get("facility_no"));
        auditService.record(user.getId(), "QRCODE_REBIND", "FACILITY_QR_CODE", String.valueOf(id), details);
        return ApiResponse.success(jdbcTemplate.queryForMap(
                "SELECT q.id,q.token,q.status,q.facility_id,f.facility_no,f.name AS facility_name,q.bound_at " +
                "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id WHERE q.id=?", id));
    }

    private Map<String, Object> findQr(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,token,serial_no,school,campus,building,floor,location_no,status,facility_id,deleted_at FROM facility_qr_code WHERE id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("二维码不存在");
        return rows.get(0);
    }

    private Map<String, Object> qrDetails(Map<String, Object> qr) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (String key : new String[]{"serial_no", "school", "campus", "building", "floor", "location_no", "status", "facility_id"}) {
            details.put(key, qr.get(key));
        }
        return details;
    }

    @GetMapping("/resolve/{token}")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> pool = jdbcTemplate.queryForList(
                "SELECT serial_no,school,campus,building,floor,location_no,status,facility_id FROM facility_qr_code WHERE token=?", token);
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
        }
    }

    public static class RebindRequest {
        @NotNull(message = "请选择需要绑定的设施")
        public Long facilityId;
    }

    public static class BatchDeleteRequest {
        @NotNull(message = "请选择需要删除的二维码")
        public List<Long> ids;
    }
}
