package com.campusfire.inspection;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inspection-plans")
public class InspectionPlanController {
    private final JdbcTemplate jdbcTemplate; private final AuthService authService; private final AuditService auditService;
    public InspectionPlanController(JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService){this.jdbcTemplate=jdbcTemplate;this.authService=authService;this.auditService=auditService;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(Authentication a){requireAdmin(a);return ApiResponse.success(jdbcTemplate.queryForList("SELECT p.id,p.plan_name,p.cycle_type,t.type_code AS facility_type,p.campus,p.assigned_user_id,p.start_date,p.end_date,p.enabled FROM inspection_plan p LEFT JOIN facility_type t ON t.id=p.facility_type_id WHERE p.deleted=0 ORDER BY p.id DESC"));}
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody Request r, Authentication a){AuthenticatedUser u=requireAdmin(a);Long typeId=r.facilityType==null?null:jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?",Long.class,r.facilityType);KeyHolder holder=new GeneratedKeyHolder();jdbcTemplate.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO inspection_plan(plan_name,cycle_type,facility_type_id,campus,assigned_user_id,start_date,end_date,created_by) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,r.planName);p.setString(2,r.cycleType);p.setObject(3,typeId);p.setString(4,r.campus);p.setObject(5,r.assignedUserId);p.setObject(6,r.startDate);p.setObject(7,r.endDate);p.setLong(8,u.getId());return p;},holder);Long id=holder.getKey().longValue();auditService.record(u.getId(),"INSPECTION_PLAN_CREATE","INSPECTION_PLAN",String.valueOf(id),Collections.singletonMap("planName",r.planName));return ApiResponse.success(Collections.singletonMap("id",id));}
    @PostMapping("/{id}/generate-tasks") public ApiResponse<?> generate(@PathVariable long id,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dueDate,Authentication a){AuthenticatedUser u=requireAdmin(a);Map<String,Object> p=jdbcTemplate.queryForMap("SELECT facility_type_id,campus,assigned_user_id FROM inspection_plan WHERE id=? AND enabled=1 AND deleted=0",id);String sql="INSERT IGNORE INTO inspection_task(plan_id,facility_id,assigned_user_id,due_date) SELECT ?,f.id,?,? FROM facility f WHERE f.lifecycle_status='IN_USE' AND (? IS NULL OR f.facility_type_id=?) AND (? IS NULL OR f.campus=?)";int count=jdbcTemplate.update(sql,id,p.get("assigned_user_id"),dueDate,p.get("facility_type_id"),p.get("facility_type_id"),p.get("campus"),p.get("campus"));auditService.record(u.getId(),"INSPECTION_TASK_GENERATE","INSPECTION_PLAN",String.valueOf(id),Collections.singletonMap("count",count));return ApiResponse.success(Collections.singletonMap("createdCount",count));}
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable long id,@Valid @RequestBody Request r,Authentication a){AuthenticatedUser u=requireAdmin(a);Integer exists=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_plan WHERE id=?",Integer.class,id);if(exists==null||exists==0)throw new IllegalArgumentException("巡检计划不存在");Long typeId=r.facilityType==null?null:jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?",Long.class,r.facilityType);if(r.facilityType!=null&&typeId==null)throw new IllegalArgumentException("设施类型不存在");jdbcTemplate.update("UPDATE inspection_plan SET plan_name=?,cycle_type=?,facility_type_id=?,campus=?,assigned_user_id=?,start_date=?,end_date=?,enabled=? WHERE id=?",r.planName,r.cycleType,typeId,r.campus,r.assignedUserId,r.startDate,r.endDate,r.enabled==null||r.enabled?1:0,id);auditService.record(u.getId(),"INSPECTION_PLAN_UPDATE","INSPECTION_PLAN",String.valueOf(id),Collections.singletonMap("planName",r.planName));return ApiResponse.success(Collections.singletonMap("updated",true));}
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable long id,Authentication a){AuthenticatedUser u=requireAdmin(a);Integer exists=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_plan WHERE id=? AND deleted=0",Integer.class,id);if(exists==null||exists==0)throw new IllegalArgumentException("巡检计划不存在");Integer tasks=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_task WHERE plan_id=?",Integer.class,id);if(tasks==null||tasks==0){jdbcTemplate.update("DELETE FROM inspection_plan WHERE id=?",id);}else{// 已生成任务的计划软删除：列表隐藏、停用排任务，任务与巡检记录留痕完整保留
            jdbcTemplate.update("UPDATE inspection_plan SET deleted=1,enabled=0 WHERE id=?",id);}auditService.record(u.getId(),"INSPECTION_PLAN_DELETE","INSPECTION_PLAN",String.valueOf(id),Collections.singletonMap("tasks",tasks==null?0:tasks));return ApiResponse.success(Collections.singletonMap("deleted",true));}
    private AuthenticatedUser requireAdmin(Authentication a){AuthenticatedUser u=authService.loadCurrentUser(a.getName());if(!"ADMIN".equals(u.getRoleCode()))throw new AccessDeniedException("仅管理员可管理巡检计划");return u;}
    public static class Request{@NotBlank public String planName;@NotBlank public String cycleType;public String facilityType;public String campus;public Long assignedUserId;@NotNull public LocalDate startDate;public LocalDate endDate;public Boolean enabled;}
}
