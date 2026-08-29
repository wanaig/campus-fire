package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.*;

@RestController
@RequestMapping("/maintenance-records")
public class MaintenanceRecordController {
    private final JdbcTemplate jdbcTemplate; private final AuthService authService; private final AuditService auditService;
    public MaintenanceRecordController(JdbcTemplate jdbcTemplate,AuthService authService,AuditService auditService){this.jdbcTemplate=jdbcTemplate;this.authService=authService;this.auditService=auditService;}
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) Long facilityId){return ApiResponse.success(jdbcTemplate.queryForList("SELECT m.*,f.facility_no,f.name,u.display_name AS creator FROM maintenance_record m JOIN facility f ON f.id=m.facility_id JOIN app_user u ON u.id=m.created_by WHERE (? IS NULL OR m.facility_id=?) ORDER BY m.maintenance_at DESC",facilityId,facilityId));}
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody Request r,Authentication a){AuthenticatedUser u=authService.loadCurrentUser(a.getName());if(!"ADMIN".equals(u.getRoleCode())&&!"COLLECTOR".equals(u.getRoleCode()))throw new AccessDeniedException("当前账号无维护记录权限");jdbcTemplate.update("INSERT INTO maintenance_record(facility_id,maintenance_type,maintenance_at,maintainer,result_note,next_maintenance_at,created_by) VALUES(?,?,?,?,?,?,?)",r.facilityId,r.maintenanceType,r.maintenanceAt,r.maintainer,r.resultNote,r.nextMaintenanceAt,u.getId());if(r.nextMaintenanceAt!=null)jdbcTemplate.update("UPDATE facility SET last_maintenance_at=?,next_maintenance_at=? WHERE id=?",r.maintenanceAt,r.nextMaintenanceAt,r.facilityId);auditService.record(u.getId(),"MAINTENANCE_CREATE","FACILITY",String.valueOf(r.facilityId),Collections.singletonMap("type",r.maintenanceType));return ApiResponse.success(Collections.singletonMap("saved",true));}
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable long id,@Valid @RequestBody Request r,Authentication a){AuthenticatedUser u=authService.loadCurrentUser(a.getName());if(!"ADMIN".equals(u.getRoleCode())&&!"COLLECTOR".equals(u.getRoleCode()))throw new AccessDeniedException("当前账号无维护记录权限");int updated=jdbcTemplate.update("UPDATE maintenance_record SET facility_id=?,maintenance_type=?,maintenance_at=?,maintainer=?,result_note=?,next_maintenance_at=? WHERE id=?",r.facilityId,r.maintenanceType,r.maintenanceAt,r.maintainer,r.resultNote,r.nextMaintenanceAt,id);if(updated==0)throw new IllegalArgumentException("维护记录不存在");if(r.nextMaintenanceAt!=null)jdbcTemplate.update("UPDATE facility SET last_maintenance_at=?,next_maintenance_at=? WHERE id=?",r.maintenanceAt,r.nextMaintenanceAt,r.facilityId);auditService.record(u.getId(),"MAINTENANCE_UPDATE","FACILITY",String.valueOf(r.facilityId),Collections.singletonMap("type",r.maintenanceType));return ApiResponse.success(Collections.singletonMap("updated",true));}
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable long id,Authentication a){AuthenticatedUser u=authService.loadCurrentUser(a.getName());if(!"ADMIN".equals(u.getRoleCode())&&!"COLLECTOR".equals(u.getRoleCode()))throw new AccessDeniedException("当前账号无维护记录权限");int deleted=jdbcTemplate.update("DELETE FROM maintenance_record WHERE id=?",id);if(deleted==0)throw new IllegalArgumentException("维护记录不存在");auditService.record(u.getId(),"MAINTENANCE_DELETE","MAINTENANCE_RECORD",String.valueOf(id),null);return ApiResponse.success(Collections.singletonMap("deleted",true));}
    public static class Request{@javax.validation.constraints.NotNull public Long facilityId;@NotBlank public String maintenanceType;@NotBlank public String maintenanceAt;@NotBlank public String maintainer;@NotBlank public String resultNote;public String nextMaintenanceAt;}
}
