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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        List<Map<String, Object>> rows;
        if (ids != null && !ids.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            Object[] args = new Object[ids.size()];
            for (int i = 0; i < ids.size(); i++) args[i] = ids.get(i);
            rows = jdbcTemplate.queryForList("SELECT q.token,q.serial_no,q.campus,q.building,q.floor,q.group_no,f.facility_no,f.name AS facility_name " +
                    "FROM (SELECT id,token,serial_no,campus,building,floor,facility_id," +
                    "ROW_NUMBER() OVER (PARTITION BY campus,building,floor ORDER BY serial_no) AS group_no " +
                    "FROM facility_qr_code) q LEFT JOIN facility f ON f.id=q.facility_id " +
                    "WHERE q.id IN (" + placeholders + ") ORDER BY q.serial_no", args);
        } else {
            rows = jdbcTemplate.queryForList("SELECT q.token,q.serial_no,q.campus,q.building,q.floor,q.group_no,f.facility_no,f.name AS facility_name " +
                    "FROM (SELECT id,token,serial_no,campus,building,floor,status,facility_id," +
                    "ROW_NUMBER() OVER (PARTITION BY campus,building,floor ORDER BY serial_no) AS group_no " +
                    "FROM facility_qr_code) q LEFT JOIN facility f ON f.id=q.facility_id " +
                    "WHERE (? IS NULL OR q.status=?) ORDER BY q.serial_no", status, status);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("没有符合条件的二维码");
        List<PdfService.LabelData> labels = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String serial = String.valueOf(row.get("serial_no"));
            String location = joinLocation(row);
            String facility = row.get("facility_no") == null ? "" : String.valueOf(row.get("facility_name")) + "（" + row.get("facility_no") + "）";
            String displayCode = labelDisplayCode(row, serial);
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
        for (String key : new String[]{"campus", "building", "floor"}) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isEmpty()) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private String labelDisplayCode(Map<String, Object> row, String serial) {
        String building = value(row.get("building"));
        String floor = value(row.get("floor"));
        Object groupNo = row.get("group_no");
        if (!building.isEmpty() && !floor.isEmpty() && groupNo instanceof Number) {
            return "NO." + building + "-" + floor + "-" + String.format("%02d", ((Number) groupNo).intValue());
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
        boolean grouped = request.campus != null && !request.campus.trim().isEmpty()
                && request.building != null && !request.building.trim().isEmpty()
                && request.floor != null && !request.floor.trim().isEmpty();
        int total = 0;
        int nextSerial = next;
        for (BatchRequest.Group group : request.groups == null ? Collections.<BatchRequest.Group>emptyList() : request.groups) {
            if (group == null) continue;
            int[] result = generateGroup(group, nextSerial, created);
            total += result[0];
            nextSerial = result[1];
        }
        if (!grouped && request.count != null && request.count > 0) {
            int count = Math.min(request.count, 500);
            for (int i = 1; i <= count; i++) {
                String token = UUID.randomUUID().toString().replace("-", "");
                jdbcTemplate.update("INSERT INTO facility_qr_code(token,serial_no) VALUES(?,?)", token, ++nextSerial);
                created.add(row(token, nextSerial, null, null, null, i));
            }
            total += count;
        }
        auditService.record(user.getId(), "QRCODE_BATCH_CREATE", "FACILITY_QR_CODE", "batch",
                Collections.singletonMap("count", total));
        return ApiResponse.success(created);
    }

    private int[] generateGroup(BatchRequest.Group group, int startSerial, List<Map<String, Object>> created) {
        int count = Math.max(1, Math.min(group.count, 500));
        String campus = group.campus == null ? "" : group.campus.trim();
        String building = group.building == null ? "" : group.building.trim();
        String floor = group.floor == null ? "" : group.floor.trim();
        int floorBase = group.startNo == null ? 1 : Math.max(1, group.startNo);
        int serial = startSerial;
        for (int i = 0; i < count; i++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            jdbcTemplate.update("INSERT INTO facility_qr_code(token,serial_no,campus,building,floor) VALUES(?,?,?,?,?)",
                    token, ++serial, campus, building, floor);
            created.add(row(token, serial, campus, building, floor, floorBase + i));
        }
        return new int[]{count, serial};
    }

    private Map<String, Object> row(String token, int serial, String campus, String building, String floor, int groupNo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("token", token);
        row.put("serialNo", serial);
        row.put("campus", campus);
        row.put("building", building);
        row.put("floor", floor);
        row.put("groupNo", groupNo);
        row.put("status", "UNCLAIMED");
        return row;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String keyword,
                                                       Authentication authentication) {
        requireAdmin(authentication);
        String like = keyword == null ? "" : "%" + keyword + "%";
        String sql = "SELECT q.id,q.token,q.serial_no,q.campus,q.building,q.floor,q.status,q.created_at,q.bound_at," +
                "f.facility_no,f.name AS facility_name " +
                "FROM facility_qr_code q LEFT JOIN facility f ON f.id=q.facility_id " +
                "WHERE (? IS NULL OR q.status=?) AND (?='' OR q.token LIKE ? OR f.facility_no LIKE ? OR f.name LIKE ? OR q.campus LIKE ? OR q.building LIKE ? OR q.floor LIKE ?) " +
                "ORDER BY q.serial_no DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, status, status, like, like, like, like, like, like, like));
    }

    @GetMapping("/resolve/{token}")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> pool = jdbcTemplate.queryForList(
                "SELECT serial_no,campus,building,floor,status,facility_id FROM facility_qr_code WHERE token=?", token);
        if (!pool.isEmpty()) {
            Map<String, Object> qr = pool.get(0);
            String status = String.valueOf(qr.get("status"));
            if ("UNCLAIMED".equals(status)) {
                result.put("type", "BLANK");
                result.put("serialNo", qr.get("serial_no"));
                result.put("campus", qr.get("campus"));
                result.put("building", qr.get("building"));
                result.put("floor", qr.get("floor"));
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
            public String campus;
            public String building;
            public String floor;
            @Min(value = 1, message = "每层至少生成 1 个")
            @Max(value = 500, message = "每层最多生成 500 个")
            public Integer count;
            public Integer startNo;
        }
    }
}
