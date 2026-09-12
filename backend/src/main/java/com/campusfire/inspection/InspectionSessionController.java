package com.campusfire.inspection;

import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import com.campusfire.audit.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 设施驱动巡检：保安扫码即可对设施开始巡检，不再依赖巡检计划生成的任务。
 * 系统以设施为主体统计最近巡检时间，超期未巡检（默认 30 天）时给出提醒。
 */
@RestController
@RequestMapping("/inspection")
public class InspectionSessionController {
    private final JdbcTemplate jdbcTemplate; private final AuthService authService; private final ObjectMapper objectMapper; private final AuditService auditService; private final InspectionResultSupport resultSupport;
    public InspectionSessionController(JdbcTemplate jdbcTemplate, AuthService authService,ObjectMapper objectMapper, AuditService auditService, InspectionResultSupport resultSupport){this.jdbcTemplate=jdbcTemplate;this.authService=authService;this.objectMapper=objectMapper;this.auditService=auditService;this.resultSupport=resultSupport;}

    /** 扫码开始巡检：按二维码定位设施，任何保安均可随时发起，同一设施本人未完成的会话可直接续检 */
    @PostMapping("/sessions")
    public ApiResponse<?> start(@Valid @RequestBody StartRequest r,Authentication a){AuthenticatedUser u=requireGuard(a);
        Map<String,Object> facility=resolveFacilityByQr(r.qrToken);
        if(facility==null)throw new IllegalArgumentException("二维码无效或不属于任何设施");
        if(!"IN_USE".equals(String.valueOf(facility.get("lifecycle_status"))))throw new IllegalArgumentException("该设施已停用或报废，不能巡检");
        LocationProximity.validateCoordinates(r.latitude,r.longitude);
        Double distance=facility.get("latitude")==null||facility.get("longitude")==null?null:LocationProximity.distanceMeters((Number)facility.get("latitude"),(Number)facility.get("longitude"),r.latitude,r.longitude);
        double allowedDistance=LocationProximity.allowedDistanceMeters((Number)facility.get("location_accuracy_meters"),r.accuracyMeters);
        if(distance!=null&&distance>allowedDistance)throw new IllegalArgumentException(String.format("当前定位距离设施约%d米（本次允许%d米），请靠近设施后重试",Math.round(distance),Math.round(allowedDistance)));
        Long facilityId=((Number)facility.get("id")).longValue();
        jdbcTemplate.update("UPDATE inspection_session SET status='CANCELLED' WHERE facility_id=? AND user_id=? AND status='STARTED' AND started_at<NOW()-INTERVAL 2 HOUR",facilityId,u.getId());
        List<Map<String,Object>> resumed=jdbcTemplate.queryForList("SELECT id,started_at FROM inspection_session WHERE facility_id=? AND user_id=? AND status='STARTED' ORDER BY started_at DESC LIMIT 1",facilityId,u.getId());
        if(!resumed.isEmpty()){Map<String,Object> s=resumed.get(0);jdbcTemplate.update("UPDATE inspection_session SET latitude=?,longitude=?,location_accuracy_meters=?,location_distance_meters=? WHERE id=?",r.latitude,r.longitude,r.accuracyMeters,distance,s.get("id"));return ApiResponse.success(new SessionResponse((String)s.get("id"),facilityId,((java.sql.Timestamp)s.get("started_at")).toInstant(),r.latitude,r.longitude));}
        String sessionId=UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO inspection_session(id,facility_id,user_id,qr_token_snapshot,latitude,longitude,location_accuracy_meters,location_distance_meters) VALUES(?,?,?,?,?,?,?,?)",sessionId,facilityId,u.getId(),r.qrToken,r.latitude,r.longitude,r.accuracyMeters,distance);
        return ApiResponse.success(new SessionResponse(sessionId,facilityId,Instant.now(),r.latitude,r.longitude));}

    /** 设施巡检状态：以消防栓等设施为主体，统计最近巡检时间并标记超期（默认 30 天）；公开只读，访客可不登录查看 */
    @GetMapping("/status")
    public ApiResponse<Map<String,Object>> status(@RequestParam(required=false) String keyword,
                                                  @RequestParam(required=false) String facilityType,
                                                  @RequestParam(required=false) String campus,
                                                  @RequestParam(defaultValue="30") int overdueDays){
        int days=Math.max(1,Math.min(overdueDays,365));
        String pattern=keyword==null||keyword.trim().isEmpty()?null:"%"+keyword.trim()+"%";
        List<Map<String,Object>> rows=jdbcTemplate.queryForList(
                "SELECT f.id,f.facility_no AS facilityNo,f.name,ft.type_code AS facilityType,ft.type_name AS facilityTypeName," +
                        "f.campus,f.building,f.floor,f.area,f.detail_location AS detailLocation," +
                        "last_inspection.last_inspected_at AS lastInspectedAtRaw,last_inspection.last_inspector AS lastInspector," +
                        "(SELECT COUNT(*) FROM rectification_order ro WHERE ro.facility_id=f.id AND ro.status='OPEN') AS open_rectification_count," +
                        "(SELECT COUNT(*) FROM facility_component fc LEFT JOIN inspection_item ii ON ii.facility_type_id=f.facility_type_id AND ii.item_code=fc.item_code " +
                        "WHERE fc.facility_id=f.id AND fc.manufacture_date IS NOT NULL AND " +
                        "(CASE WHEN ii.maintenance_year_threshold IS NOT NULL THEN CASE WHEN YEAR(fc.manufacture_date)<ii.maintenance_year_threshold " +
                        "THEN ii.maintenance_cycle_before_months ELSE ii.maintenance_cycle_after_months END ELSE ii.maintenance_cycle_months END) IS NOT NULL AND " +
                        "TIMESTAMPADD(MONTH,(CASE WHEN ii.maintenance_year_threshold IS NOT NULL THEN CASE WHEN YEAR(fc.manufacture_date)<ii.maintenance_year_threshold " +
                        "THEN ii.maintenance_cycle_before_months ELSE ii.maintenance_cycle_after_months END ELSE ii.maintenance_cycle_months END),COALESCE((SELECT MAX(m.maintenance_at) FROM maintenance_record m WHERE m.facility_id=fc.facility_id AND m.component_code=fc.item_code),fc.manufacture_date)) " +
                        "<= DATE_ADD(CURDATE(), INTERVAL 7 DAY)) AS due_component_count," +
                        "(SELECT COUNT(*) FROM facility_component fc LEFT JOIN inspection_item ii ON ii.facility_type_id=f.facility_type_id AND ii.item_code=fc.item_code " +
                        "WHERE fc.facility_id=f.id AND fc.manufacture_date IS NOT NULL AND " +
                        "(CASE WHEN ii.maintenance_year_threshold IS NOT NULL THEN CASE WHEN YEAR(fc.manufacture_date)<ii.maintenance_year_threshold " +
                        "THEN ii.maintenance_cycle_before_months ELSE ii.maintenance_cycle_after_months END ELSE ii.maintenance_cycle_months END) IS NOT NULL) AS maintenance_component_count," +
                        "(SELECT MIN(TIMESTAMPADD(MONTH,(CASE WHEN ii.maintenance_year_threshold IS NOT NULL THEN CASE WHEN YEAR(fc.manufacture_date)<ii.maintenance_year_threshold " +
                         "THEN ii.maintenance_cycle_before_months ELSE ii.maintenance_cycle_after_months END ELSE ii.maintenance_cycle_months END),COALESCE((SELECT MAX(m.maintenance_at) FROM maintenance_record m WHERE m.facility_id=fc.facility_id AND m.component_code=fc.item_code),fc.manufacture_date))) " +
                        "FROM facility_component fc LEFT JOIN inspection_item ii ON ii.facility_type_id=f.facility_type_id AND ii.item_code=fc.item_code " +
                        "WHERE fc.facility_id=f.id AND fc.manufacture_date IS NOT NULL AND " +
                        "(CASE WHEN ii.maintenance_year_threshold IS NOT NULL THEN CASE WHEN YEAR(fc.manufacture_date)<ii.maintenance_year_threshold " +
                        "THEN ii.maintenance_cycle_before_months ELSE ii.maintenance_cycle_after_months END ELSE ii.maintenance_cycle_months END) IS NOT NULL AND " +
                        "TIMESTAMPADD(MONTH,(CASE WHEN ii.maintenance_year_threshold IS NOT NULL THEN CASE WHEN YEAR(fc.manufacture_date)<ii.maintenance_year_threshold " +
                         "THEN ii.maintenance_cycle_before_months ELSE ii.maintenance_cycle_after_months END ELSE ii.maintenance_cycle_months END),COALESCE((SELECT MAX(m.maintenance_at) FROM maintenance_record m WHERE m.facility_id=fc.facility_id AND m.component_code=fc.item_code),fc.manufacture_date)) " +
                         "IS NOT NULL) AS next_maintenance_due " +
                        "FROM facility f JOIN facility_type ft ON ft.id=f.facility_type_id " +
                        "LEFT JOIN (SELECT m.facility_id,m.last_inspected_at," +
                        "(SELECT u.display_name FROM inspection_record r2 JOIN app_user u ON u.id=r2.user_id WHERE r2.facility_id=m.facility_id AND r2.submitted_at=m.last_inspected_at LIMIT 1) AS last_inspector " +
                        "FROM (SELECT r.facility_id,MAX(r.submitted_at) AS last_inspected_at FROM inspection_record r " +
                        "LEFT JOIN inspection_record_correction c ON c.record_id=r.id WHERE c.id IS NULL GROUP BY r.facility_id) m) last_inspection " +
                        "ON last_inspection.facility_id=f.id " +
                        "WHERE f.lifecycle_status='IN_USE' " +
                        "AND (? IS NULL OR f.facility_no LIKE ? OR f.name LIKE ? OR f.building LIKE ? OR f.detail_location LIKE ?) " +
                        "AND (? IS NULL OR ft.type_code=?) AND (? IS NULL OR f.campus=?) " +
                        "ORDER BY last_inspection.last_inspected_at IS NULL DESC, last_inspection.last_inspected_at, f.id",
                pattern,pattern,pattern,pattern,pattern,
                (facilityType==null||facilityType.isEmpty())?null:facilityType,facilityType,
                (campus==null||campus.isEmpty())?null:campus,campus);
        LocalDate today=LocalDate.now();
        List<Map<String,Object>> items=new ArrayList<>();
        int never=0,overdue=0,recent=0;
        for(Map<String,Object> row:rows){
            Map<String,Object> item=new LinkedHashMap<>(row);
            item.remove("open_rectification_count");
            item.remove("lastInspectedAtRaw");
            item.put("openRectificationCount",((Number)row.get("open_rectification_count")).intValue());
            item.put("dueComponentCount",((Number)row.get("due_component_count")).intValue());
            item.put("maintenanceComponentCount",((Number)row.get("maintenance_component_count")).intValue());
            Object dueDate=row.get("next_maintenance_due");
            item.put("nextMaintenanceDue",dueDate==null?null:dueDate.toString().substring(0,10));
            LocalDateTime lastAt=row.get("lastInspectedAtRaw")==null?null:((java.sql.Timestamp)row.get("lastInspectedAtRaw")).toLocalDateTime();
            Long daysSince=lastAt==null?null:ChronoUnit.DAYS.between(lastAt.toLocalDate(),today);
            String state;
            if(lastAt==null){state="NEVER";never++;}
            else if(daysSince>days){state="OVERDUE";overdue++;}
            else{state="NORMAL";recent++;}
            item.put("lastInspectedAt",lastAt==null?null:lastAt.toString());
            item.put("daysSinceInspection",daysSince);
            item.put("inspectionState",state);
            items.add(item);
        }
        Map<String,Object> data=new LinkedHashMap<>();
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("total",items.size());summary.put("neverInspected",never);summary.put("overdue",overdue+never);summary.put("recent",recent);
        summary.put("overdueDays",days);
        data.put("summary",summary);data.put("items",items);
        return ApiResponse.success(data);}

    /** 保安逐项确认的部件清单：只认采集员建档勾选登记的档案部件，类型检查项仅补充检查标准文案 */
    @GetMapping("/sessions/{sessionId}/components")
    public ApiResponse<?> components(@PathVariable String sessionId, Authentication a) {
        AuthenticatedUser u = current(a);
        Long facilityId = sessionFacility(sessionId, u.getId());
        List<Map<String, Object>> items = new ArrayList<>();
        for (InspectionResultSupport.CheckItem item : resultSupport.loadFacilityCheckItems(facilityId).values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item_code", item.itemCode);
            row.put("item_name", item.itemName);
            row.put("inspection_standard", item.standard);
            row.put("required_flag", true);
            items.add(row);
        }
        return ApiResponse.success(items);
    }

    @PostMapping("/sessions/{sessionId}/draft")
    public ApiResponse<?> draft(@PathVariable String sessionId,@Valid @RequestBody DraftRequest r,Authentication a){AuthenticatedUser u=requireGuard(a);Integer count=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_session WHERE id=? AND user_id=? AND status='STARTED'",Integer.class,sessionId,u.getId());if(count==null||count==0)throw new AccessDeniedException("巡检会话无效或不属于当前账号");try{String json=objectMapper.writeValueAsString(r.results);jdbcTemplate.update("INSERT INTO inspection_draft(session_id,results_json,note) VALUES(?,?,?) ON DUPLICATE KEY UPDATE results_json=VALUES(results_json),note=VALUES(note)",sessionId,json,r.note);}catch(JsonProcessingException e){throw new IllegalArgumentException("检查项数据格式不正确");}return ApiResponse.success(Collections.singletonMap("saved",true));}

    @GetMapping("/sessions/{sessionId}/draft")
    public ApiResponse<?> getDraft(@PathVariable String sessionId,Authentication a){AuthenticatedUser u=current(a);Integer count=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_session WHERE id=? AND user_id=? AND status='STARTED'",Integer.class,sessionId,u.getId());if(count==null||count==0)throw new AccessDeniedException("巡检会话无效或不属于当前账号");List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT results_json,note FROM inspection_draft WHERE session_id=?",sessionId);if(rows.isEmpty())return ApiResponse.success(null);try{Map<String,Object> draft=new HashMap<>();draft.put("results",objectMapper.readValue(String.valueOf(rows.get(0).get("results_json")),Map.class));draft.put("note",rows.get(0).get("note"));return ApiResponse.success(draft);}catch(JsonProcessingException e){return ApiResponse.success(null);}}

    @PostMapping("/sessions/{sessionId}/submit")
    @Transactional
    public ApiResponse<?> submit(@PathVariable String sessionId, Authentication a) {
        AuthenticatedUser u = requireGuard(a);
        Map<String, Object> session = jdbcTemplate.queryForMap("SELECT id,facility_id,task_id FROM inspection_session WHERE id=? AND user_id=? AND status='STARTED'", sessionId, u.getId());
        Map<String, Object> draft = jdbcTemplate.queryForMap("SELECT results_json,note FROM inspection_draft WHERE session_id=?", sessionId);
        Integer photoCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_photo WHERE session_id=?", Integer.class, sessionId);
        if (draft.get("results_json") == null || String.valueOf(draft.get("results_json")).trim().isEmpty()) {
            throw new IllegalArgumentException("请先完成巡检检查项记录");
        }
        if (photoCount == null || photoCount < 1) {
            throw new IllegalArgumentException("请至少拍摄1张现场照片留痕");
        }
        String recordId = UUID.randomUUID().toString();
        Long facilityId = sessionFacility(sessionId, u.getId());
        Long taskId = session.get("task_id") == null ? null : ((Number) session.get("task_id")).longValue();
        Map<String, String> results = validateResults(facilityId, draft);
        jdbcTemplate.update("INSERT INTO inspection_record(id,session_id,task_id,facility_id,user_id,results_json,note,photo_count) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                recordId, sessionId, taskId, facilityId, u.getId(), draft.get("results_json"), draft.get("note"), photoCount == null ? 0 : photoCount);
        createRectificationIfNeeded(recordId, taskId, facilityId, results);
        flagRapidInspection(u.getId(), sessionId);
        jdbcTemplate.update("UPDATE inspection_session SET status='SUBMITTED' WHERE id=? AND status='STARTED'", sessionId);
        auditService.record(u.getId(), "INSPECTION_SUBMIT", "INSPECTION_RECORD", recordId,
                Collections.singletonMap("sessionId", sessionId));
        return ApiResponse.success(new SubmitResponse(recordId, sessionId, facilityId, photoCount == null ? 0 : photoCount, Instant.now()));
    }

    private void flagRapidInspection(long userId, String sessionId) {
        Integer recent = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_record WHERE user_id=? AND submitted_at>=DATE_SUB(NOW(),INTERVAL 10 MINUTE)", Integer.class, userId);
        if (recent != null && recent >= 3) {
            jdbcTemplate.update("INSERT INTO inspection_risk_flag(user_id,risk_type,evidence,session_id) VALUES(?,?,?,?)", userId, "RAPID_SUBMISSION", "10分钟内连续提交" + recent + "条正式巡检记录，请管理员核查现场路线和时间", sessionId);
        }
    }

    /** 提交校验：档案登记的每个部件都必须给出 正常/异常 结论；有异常必须填写说明 */
    private Map<String, String> validateResults(Long facilityId, Map<String, Object> draft) {
        try {
            Map<String, String> results = objectMapper.readValue(String.valueOf(draft.get("results_json")), Map.class);
            LinkedHashMap<String, InspectionResultSupport.CheckItem> checkItems = resultSupport.loadFacilityCheckItems(facilityId);
            if (checkItems.isEmpty()) throw new IllegalArgumentException("该设施档案未登记部件，请联系采集员补全档案后再巡检");
            for (Map.Entry<String, InspectionResultSupport.CheckItem> entry : checkItems.entrySet()) {
                String value = results.get(entry.getKey());
                if (!"PASS".equals(value) && !"FAIL".equals(value)) {
                    throw new IllegalArgumentException("请完成部件「" + entry.getValue().itemName + "」的检查确认");
                }
            }
            boolean hasAbnormal = results.values().stream().anyMatch("FAIL"::equals);
            if (hasAbnormal && (draft.get("note") == null || String.valueOf(draft.get("note")).trim().isEmpty())) {
                throw new IllegalArgumentException("存在异常部件，请填写异常情况说明");
            }
            return results;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("检查项数据格式不正确");
        }
    }

    private void createRectificationIfNeeded(String recordId, Long taskId, Long facilityId, Map<String, String> results) {
        LinkedHashMap<String, InspectionResultSupport.CheckItem> checkItems = resultSupport.loadFacilityCheckItems(facilityId);
        List<String> abnormal = new ArrayList<>();
        for (Map.Entry<String, String> entry : results.entrySet()) {
            if ("FAIL".equals(entry.getValue())) {
                InspectionResultSupport.CheckItem item = checkItems.get(entry.getKey());
                abnormal.add((item == null ? entry.getKey() : item.itemName) + "异常");
            }
        }
        if (abnormal.isEmpty()) return;
        jdbcTemplate.update("INSERT INTO rectification_order(record_id,task_id,facility_id,issue_summary,due_date) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE record_id=record_id",
                recordId, taskId, facilityId, String.join("；", abnormal), LocalDate.now().plusDays(7));
    }

    /** 二维码定位设施：优先二维码池已绑定码，兼容早期直接写设施档案的码值 */
    private Map<String, Object> resolveFacilityByQr(String qrToken) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT f.id,f.qr_token,f.name,f.facility_no,f.campus,f.building,f.floor,f.area,f.detail_location,f.latitude,f.longitude,f.location_accuracy_meters,f.lifecycle_status,ft.type_code AS facility_type " +
                        "FROM facility f LEFT JOIN facility_type ft ON ft.id=f.facility_type_id " +
                        "WHERE f.qr_token=? OR f.id=(SELECT q.facility_id FROM facility_qr_code q WHERE q.token=? AND q.status='BOUND') LIMIT 1",
                qrToken, qrToken);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 会话归属的设施：新会话直接记录 facility_id，历史会话经任务回溯 */
    private Long sessionFacility(String sessionId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT facility_id,task_id FROM inspection_session WHERE id=? AND user_id=? AND status='STARTED'", sessionId, userId);
        if (rows.isEmpty()) throw new AccessDeniedException("巡检会话无效或不属于当前账号");
        Object facilityId = rows.get(0).get("facility_id");
        if (facilityId != null) return ((Number) facilityId).longValue();
        Object taskId = rows.get(0).get("task_id");
        if (taskId == null) throw new IllegalArgumentException("巡检会话缺少设施信息");
        List<Long> ids = jdbcTemplate.queryForList("SELECT facility_id FROM inspection_task WHERE id=?", Long.class, taskId);
        if (ids.isEmpty() || ids.get(0) == null) throw new IllegalArgumentException("巡检会话缺少设施信息");
        return ids.get(0);
    }

    private AuthenticatedUser current(Authentication a){return authService.loadCurrentUser(a.getName());}

    /** 保安巡检写操作仅限保安角色：管理员/采集员不能发起巡检会话、留草稿或提交记录 */
    private AuthenticatedUser requireGuard(Authentication a){
        AuthenticatedUser u=current(a);
        if(!"GUARD".equals(u.getRoleCode()))throw new AccessDeniedException("仅保安账号可以执行巡检操作");
        return u;
    }
    public static class StartRequest{@NotBlank public String qrToken;@NotNull public BigDecimal latitude;@NotNull public BigDecimal longitude;public BigDecimal accuracyMeters;}
    public static class DraftRequest{@NotNull public Map<String,String> results;public String note;}
    public static class SessionResponse{public String sessionId;public Long facilityId;public Instant serverStartedAt;public BigDecimal latitude;public BigDecimal longitude;SessionResponse(String s,Long f,Instant i,BigDecimal lat,BigDecimal lon){sessionId=s;facilityId=f;serverStartedAt=i;latitude=lat;longitude=lon;}}
    public static class SubmitResponse{public String recordId;public String sessionId;public Long facilityId;public Integer photoCount;public Instant submittedAt;SubmitResponse(String r,String s,Long f,Integer p,Instant i){recordId=r;sessionId=s;facilityId=f;photoCount=p;submittedAt=i;}}
}
