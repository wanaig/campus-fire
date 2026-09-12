package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/facility-operations")
public class FacilityOperationsController {
    private final FacilityService facilityService;
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AuditService auditService;

    public FacilityOperationsController(FacilityService facilityService, JdbcTemplate jdbcTemplate, AuthService authService, AuditService auditService) {
        this.facilityService=facilityService; this.jdbcTemplate=jdbcTemplate; this.authService=authService; this.auditService=auditService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        String csv="设施编号,设施类型代码,设施名称,校区,楼栋,楼层,区域,详细位置,品牌,型号,规格,生产日期,投用日期\r\n"+
                "XF-001,EXTINGUISHER,教学楼灭火器,主校区,1号教学楼,1层,东侧走廊,消防栓旁,示例品牌,MFZ/ABC4,4kg干粉,2026-01-01,2026-02-01\r\n";
        byte[] bytes=("\ufeff"+csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=facility-import-template.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);
    }

    @PostMapping("/batch")
    public ApiResponse<?> batch(@Valid @RequestBody BatchRequest request, Authentication authentication) {
        AuthenticatedUser user=current(authentication); requireEditor(user);
        List<Map<String,Object>> failures=new ArrayList<>(); int success=0;
        for(int i=0;i<request.items.size();i++) try { facilityService.create(request.items.get(i),user.getId(),user.getRoleCode()); success++; }
        catch(Exception e){Map<String,Object> failure=new LinkedHashMap<>();failure.put("row",i+1);failure.put("facilityNo",request.items.get(i).facilityNo);failure.put("message",e.getMessage());failures.add(failure);}
        Map<String,Object> result=new LinkedHashMap<>();result.put("successCount",success);result.put("failureCount",failures.size());result.put("failures",failures);return ApiResponse.success(result);
    }

    @GetMapping("/{id}/history")
    public ApiResponse<?> history(@PathVariable long id) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("lifecycle",jdbcTemplate.queryForList("SELECT e.id,e.event_type,e.event_note,e.event_at,u.display_name AS operator FROM facility_lifecycle_event e JOIN app_user u ON u.id=e.operator_user_id WHERE e.facility_id=? ORDER BY e.event_at DESC",id));
        data.put("maintenance",jdbcTemplate.queryForList("SELECT m.id,m.maintenance_type,m.maintenance_at,m.maintainer,m.result_note,m.next_maintenance_at,m.evidence_urls,u.display_name AS creator FROM maintenance_record m JOIN app_user u ON u.id=m.created_by WHERE m.facility_id=? ORDER BY m.maintenance_at DESC",id));
        data.put("inspections",jdbcTemplate.queryForList("SELECT r.id,r.submitted_at,r.note,r.photo_count,u.display_name AS inspector FROM inspection_record r JOIN app_user u ON u.id=r.user_id WHERE r.facility_id=? ORDER BY r.submitted_at DESC",id));
        return ApiResponse.success(data);
    }

    @PostMapping("/{id}/lifecycle")
    public ApiResponse<?> lifecycle(@PathVariable long id,@Valid @RequestBody LifecycleRequest request,Authentication authentication){
        AuthenticatedUser user=current(authentication);if(!"ADMIN".equals(user.getRoleCode()))throw new AccessDeniedException("仅管理员可变更设施生命周期");
        String current=jdbcTemplate.queryForObject("SELECT lifecycle_status FROM facility WHERE id=?",String.class,id);String status=statusFor(request.eventType);if(!allowed(current,status))throw new IllegalArgumentException("当前状态不允许执行该变更");jdbcTemplate.update("UPDATE facility SET lifecycle_status=? WHERE id=?",status,id);
        jdbcTemplate.update("INSERT INTO facility_lifecycle_event(facility_id,event_type,event_note,operator_user_id) VALUES(?,?,?,?)",id,request.eventType,request.note,user.getId());
        auditService.record(user.getId(),"FACILITY_LIFECYCLE_"+request.eventType,"FACILITY",String.valueOf(id),Collections.singletonMap("note",request.note));return ApiResponse.success(Collections.singletonMap("status",status));
    }

    private String statusFor(String event){if("RETIRED".equals(event))return "RETIRED";if("SCRAPPED".equals(event))return "SCRAPPED";if("SUSPENDED".equals(event))return "SUSPENDED";if("RESTORED".equals(event))return "IN_USE";throw new IllegalArgumentException("不支持的生命周期事件");}
    private boolean allowed(String current,String next){if(current==null)return false;if("SCRAPPED".equals(current))return false;if("IN_USE".equals(current))return "SUSPENDED".equals(next)||"RETIRED".equals(next)||"SCRAPPED".equals(next);if("SUSPENDED".equals(current)||"RETIRED".equals(current))return "IN_USE".equals(next)||"SCRAPPED".equals(next);return false;}
    private AuthenticatedUser current(Authentication a){return authService.loadCurrentUser(a.getName());}
    private void requireEditor(AuthenticatedUser u){if(!"ADMIN".equals(u.getRoleCode())&&!"COLLECTOR".equals(u.getRoleCode()))throw new AccessDeniedException("当前账号无批量建档权限");}
    public static class BatchRequest{@NotEmpty public List<FacilityDtos.CreateRequest> items;}
    public static class LifecycleRequest{@NotBlank public String eventType;@NotBlank public String note;}
}
