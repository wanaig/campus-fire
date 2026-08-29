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
import java.util.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/inspection")
public class InspectionSessionController {
    private final JdbcTemplate jdbcTemplate; private final AuthService authService; private final ObjectMapper objectMapper; private final AuditService auditService;
    public InspectionSessionController(JdbcTemplate jdbcTemplate, AuthService authService,ObjectMapper objectMapper, AuditService auditService){this.jdbcTemplate=jdbcTemplate;this.authService=authService;this.objectMapper=objectMapper;this.auditService=auditService;}

    @GetMapping("/tasks")
    public ApiResponse<List<Map<String,Object>>> tasks(Authentication a,@RequestParam(required=false) String status){AuthenticatedUser u=current(a);String sql="SELECT t.id,t.due_date,t.status,f.id AS facility_id,f.facility_no,f.name,f.campus,f.building,f.floor,f.area,f.detail_location,ft.type_code AS facility_type FROM inspection_task t JOIN facility f ON f.id=t.facility_id LEFT JOIN facility_type ft ON ft.id=f.facility_type_id WHERE (t.assigned_user_id=? OR t.assigned_user_id IS NULL) AND (? IS NULL OR t.status=?) ORDER BY t.due_date,t.id";return ApiResponse.success(jdbcTemplate.queryForList(sql,u.getId(),status,status));}

    @PostMapping("/sessions")
    public ApiResponse<?> start(@Valid @RequestBody StartRequest r,Authentication a){AuthenticatedUser u=current(a);Map<String,Object> task=jdbcTemplate.queryForMap("SELECT t.id,t.facility_id,t.assigned_user_id,t.status,f.qr_token,f.latitude,f.longitude FROM inspection_task t JOIN facility f ON f.id=t.facility_id WHERE t.id=?",r.taskId);if(task.get("assigned_user_id")!=null&&!u.getId().equals(((Number)task.get("assigned_user_id")).longValue())&&!"ADMIN".equals(u.getRoleCode()))throw new AccessDeniedException("当前任务未分配给此账号");
        if("IN_PROGRESS".equals(task.get("status"))){List<Map<String,Object>> resumed=jdbcTemplate.queryForList("SELECT id,latitude,longitude,started_at FROM inspection_session WHERE task_id=? AND user_id=? AND status='STARTED' ORDER BY started_at DESC LIMIT 1",r.taskId,u.getId());if(!resumed.isEmpty()){Map<String,Object> s=resumed.get(0);return ApiResponse.success(new SessionResponse((String)s.get("id"),r.taskId,((java.sql.Timestamp)s.get("started_at")).toInstant(),(BigDecimal)s.get("latitude"),(BigDecimal)s.get("longitude")));}throw new IllegalArgumentException("任务正在由其他人员巡检，暂不能开始");}
        if(!"PENDING".equals(task.get("status")))throw new IllegalArgumentException("任务当前不可开始");if(!Objects.equals(task.get("qr_token"),r.qrToken))throw new IllegalArgumentException("二维码无效或不属于当前任务");Double distance=distance(task.get("latitude"),task.get("longitude"),r.latitude,r.longitude);if(distance!=null&&distance>Math.max(r.allowedDistanceMeters==null?100:r.allowedDistanceMeters,10))throw new IllegalArgumentException("当前定位不在设施附近，不能开始巡检");String sessionId=UUID.randomUUID().toString();int updated=jdbcTemplate.update("UPDATE inspection_task SET status='IN_PROGRESS' WHERE id=? AND status='PENDING'",r.taskId);if(updated==0)throw new IllegalArgumentException("任务已被开始，请刷新后重试");jdbcTemplate.update("INSERT INTO inspection_session(id,task_id,user_id,qr_token_snapshot,latitude,longitude,location_distance_meters) VALUES(?,?,?,?,?,?,?)",sessionId,r.taskId,u.getId(),r.qrToken,r.latitude,r.longitude,distance);return ApiResponse.success(new SessionResponse(sessionId,r.taskId,Instant.now(),r.latitude,r.longitude));}

    @PostMapping("/sessions/{sessionId}/draft")
    public ApiResponse<?> draft(@PathVariable String sessionId,@Valid @RequestBody DraftRequest r,Authentication a){AuthenticatedUser u=current(a);Integer count=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_session WHERE id=? AND task_id=? AND user_id=? AND status='STARTED'",Integer.class,sessionId,r.taskId,u.getId());if(count==null||count==0)throw new AccessDeniedException("巡检会话无效或不属于当前账号");try{String json=objectMapper.writeValueAsString(r.results);jdbcTemplate.update("INSERT INTO inspection_draft(session_id,results_json,note) VALUES(?,?,?) ON DUPLICATE KEY UPDATE results_json=VALUES(results_json),note=VALUES(note)",sessionId,json,r.note);}catch(JsonProcessingException e){throw new IllegalArgumentException("检查项数据格式不正确");}return ApiResponse.success(Collections.singletonMap("saved",true));}

    @GetMapping("/sessions/{sessionId}/draft")
    public ApiResponse<?> getDraft(@PathVariable String sessionId,Authentication a){AuthenticatedUser u=current(a);Integer count=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_session WHERE id=? AND user_id=? AND status='STARTED'",Integer.class,sessionId,u.getId());if(count==null||count==0)throw new AccessDeniedException("巡检会话无效或不属于当前账号");List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT results_json,note FROM inspection_draft WHERE session_id=?",sessionId);if(rows.isEmpty())return ApiResponse.success(null);try{Map<String,Object> draft=new HashMap<>();draft.put("results",objectMapper.readValue(String.valueOf(rows.get(0).get("results_json")),Map.class));draft.put("note",rows.get(0).get("note"));return ApiResponse.success(draft);}catch(JsonProcessingException e){return ApiResponse.success(null);}}

    @PostMapping("/sessions/{sessionId}/submit")
    @Transactional
    public ApiResponse<?> submit(@PathVariable String sessionId, Authentication a) {
        AuthenticatedUser u = current(a);
        Map<String, Object> session = jdbcTemplate.queryForMap("SELECT task_id FROM inspection_session WHERE id=? AND user_id=? AND status='STARTED'", sessionId, u.getId());
        Map<String, Object> draft = jdbcTemplate.queryForMap("SELECT results_json,note FROM inspection_draft WHERE session_id=?", sessionId);
        Integer photoCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_photo WHERE session_id=?", Integer.class, sessionId);
        if (draft.get("results_json") == null || String.valueOf(draft.get("results_json")).trim().isEmpty()) {
            throw new IllegalArgumentException("请先完成巡检检查项记录");
        }
        if (photoCount == null || photoCount < 1) {
            throw new IllegalArgumentException("请至少拍摄1张现场照片留痕");
        }
        String recordId = UUID.randomUUID().toString();
        Long taskId = ((Number) session.get("task_id")).longValue();
        Map<String, String> results = validateResults(taskId, draft);
        int inserted = jdbcTemplate.update("INSERT INTO inspection_record(id,session_id,task_id,user_id,results_json,note,photo_count) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                recordId, sessionId, taskId, u.getId(), draft.get("results_json"), draft.get("note"), photoCount == null ? 0 : photoCount);
        if (inserted == 0) throw new IllegalArgumentException("巡检记录已提交，不能重复提交");
        createRectificationIfNeeded(recordId, taskId, results);
        flagRapidInspection(u.getId(), sessionId);
        jdbcTemplate.update("UPDATE inspection_session SET status='SUBMITTED' WHERE id=? AND status='STARTED'", sessionId);
        jdbcTemplate.update("UPDATE inspection_task SET status='COMPLETED',completed_at=NOW() WHERE id=? AND status='IN_PROGRESS'", taskId);
        auditService.record(u.getId(), "INSPECTION_SUBMIT", "INSPECTION_RECORD", recordId,
                Collections.singletonMap("sessionId", sessionId));
        return ApiResponse.success(new SubmitResponse(recordId, sessionId, taskId, photoCount == null ? 0 : photoCount, Instant.now()));
    }

    private void flagRapidInspection(long userId, String sessionId) {
        Integer recent = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_record WHERE user_id=? AND submitted_at>=DATE_SUB(NOW(),INTERVAL 10 MINUTE)", Integer.class, userId);
        if (recent != null && recent >= 3) {
            jdbcTemplate.update("INSERT INTO inspection_risk_flag(user_id,risk_type,evidence,session_id) VALUES(?,?,?,?)", userId, "RAPID_SUBMISSION", "10分钟内连续提交" + recent + "条正式巡检记录，请管理员核查现场路线和时间", sessionId);
        }
    }

    private Map<String, String> validateResults(Long taskId, Map<String, Object> draft) {
        try {
            Map<String, String> results = objectMapper.readValue(String.valueOf(draft.get("results_json")), Map.class);
            List<String> requiredCodes = jdbcTemplate.queryForList(
                    "SELECT item.item_code FROM inspection_task task " +
                            "JOIN facility facility ON facility.id=task.facility_id " +
                            "JOIN inspection_item item ON item.facility_type_id=facility.facility_type_id " +
                            "WHERE task.id=? AND item.required_flag=1 AND item.enabled=1 ORDER BY item.sort_order,item.id",
                    String.class, taskId);
            for (String code : requiredCodes) {
                String value = results.get(code);
                if (!"PASS".equals(value) && !"FAIL".equals(value)) {
                    throw new IllegalArgumentException("请完成全部必检项后再提交");
                }
            }
            boolean hasAbnormal = results.values().stream().anyMatch("FAIL"::equals);
            if (hasAbnormal && (draft.get("note") == null || String.valueOf(draft.get("note")).trim().isEmpty())) {
                throw new IllegalArgumentException("存在异常项，请填写异常情况说明");
            }
            return results;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("检查项数据格式不正确");
        }
    }

    private void createRectificationIfNeeded(String recordId, Long taskId, Map<String, String> results) {
        Map<String, String> labels = jdbcTemplate.query(
                "SELECT item.item_code,item.item_name FROM inspection_task task " +
                        "JOIN facility facility ON facility.id=task.facility_id " +
                        "JOIN inspection_item item ON item.facility_type_id=facility.facility_type_id WHERE task.id=?",
                resultSet -> {
                    Map<String, String> values = new HashMap<>();
                    while (resultSet.next()) values.put(resultSet.getString("item_code"), resultSet.getString("item_name"));
                    return values;
                }, taskId);
        List<String> abnormal = new ArrayList<>();
        for (Map.Entry<String, String> entry : results.entrySet()) {
            if ("FAIL".equals(entry.getValue())) abnormal.add(labels.getOrDefault(entry.getKey(), entry.getKey()) + "异常");
        }
        if (abnormal.isEmpty()) return;
        Map<String, Object> task = jdbcTemplate.queryForMap("SELECT facility_id FROM inspection_task WHERE id=?", taskId);
        jdbcTemplate.update("INSERT INTO rectification_order(record_id,task_id,facility_id,issue_summary,due_date) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE record_id=record_id",
                recordId, taskId, ((Number) task.get("facility_id")).longValue(), String.join("；", abnormal), LocalDate.now().plusDays(7));
    }

    private AuthenticatedUser current(Authentication a){return authService.loadCurrentUser(a.getName());}
    private Double distance(Object expectedLat,Object expectedLon,BigDecimal lat,BigDecimal lon){if(expectedLat==null||expectedLon==null)return null;double dLat=((Number)expectedLat).doubleValue()-lat.doubleValue();double dLon=(((Number)expectedLon).doubleValue()-lon.doubleValue())*Math.cos(Math.toRadians(lat.doubleValue()));return Math.sqrt(dLat*dLat+dLon*dLon)*111000;}
    public static class StartRequest{@NotNull public Long taskId;@NotBlank public String qrToken;@NotNull public BigDecimal latitude;@NotNull public BigDecimal longitude;public Integer allowedDistanceMeters=100;}
    public static class DraftRequest{@NotNull public Long taskId;@NotNull public Map<String,String> results;public String note;}
    public static class SessionResponse{public String sessionId;public Long taskId;public Instant serverStartedAt;public BigDecimal latitude;public BigDecimal longitude;SessionResponse(String s,Long t,Instant i,BigDecimal lat,BigDecimal lon){sessionId=s;taskId=t;serverStartedAt=i;latitude=lat;longitude=lon;}}
    public static class SubmitResponse{public String recordId;public String sessionId;public Long taskId;public Integer photoCount;public Instant submittedAt;SubmitResponse(String r,String s,Long t,Integer p,Instant i){recordId=r;sessionId=s;taskId=t;photoCount=p;submittedAt=i;}}
}
