package com.campusfire.inspection;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inspection-items")
public class InspectionItemController {
    private final JdbcTemplate jdbcTemplate; private final AuditService auditService; private final AuthService authService;
    public InspectionItemController(JdbcTemplate jdbcTemplate, AuditService auditService, AuthService authService){this.jdbcTemplate=jdbcTemplate;this.auditService=auditService;this.authService=authService;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(@RequestParam String facilityType){return ApiResponse.success(jdbcTemplate.queryForList("SELECT i.id,t.type_code AS facility_type,i.item_code,i.item_name,i.required_flag,i.sort_order,i.enabled FROM inspection_item i JOIN facility_type t ON t.id=i.facility_type_id WHERE t.type_code=? ORDER BY i.enabled DESC,i.sort_order,i.id",facilityType));}
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody Request r, Authentication a){String role=role(a);if(!"ADMIN".equals(role))throw new AccessDeniedException("仅管理员可配置巡检检查项");Long typeId=jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?",Long.class,r.facilityType);if(typeId==null)throw new IllegalArgumentException("设施类型不存在");jdbcTemplate.update("INSERT INTO inspection_item(facility_type_id,item_code,item_name,required_flag,sort_order) VALUES(?,?,?,?,?)",typeId,r.itemCode,r.itemName,r.requiredFlag,r.sortOrder);auditService.record(userId(a),"INSPECTION_ITEM_CREATE","INSPECTION_ITEM",r.itemCode,null);return ApiResponse.success(r);}
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable long id,@Valid @RequestBody Request r,Authentication a){String role=role(a);if(!"ADMIN".equals(role))throw new AccessDeniedException("仅管理员可配置巡检检查项");Integer exists=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inspection_item WHERE id=?",Integer.class,id);if(exists==null||exists==0)throw new IllegalArgumentException("检查项不存在");Long typeId=jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?",Long.class,r.facilityType);if(typeId==null)throw new IllegalArgumentException("设施类型不存在");jdbcTemplate.update("UPDATE inspection_item SET facility_type_id=?,item_code=?,item_name=?,required_flag=?,sort_order=?,enabled=? WHERE id=?",typeId,r.itemCode,r.itemName,r.requiredFlag,r.sortOrder,r.enabled==null||r.enabled?1:0,id);auditService.record(userId(a),"INSPECTION_ITEM_UPDATE","INSPECTION_ITEM",r.itemCode,null);return ApiResponse.success(Collections.singletonMap("updated",true));}
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable long id,Authentication a){String role=role(a);if(!"ADMIN".equals(role))throw new AccessDeniedException("仅管理员可配置巡检检查项");int n=jdbcTemplate.update("DELETE FROM inspection_item WHERE id=?",id);if(n==0)throw new IllegalArgumentException("检查项不存在");auditService.record(userId(a),"INSPECTION_ITEM_DELETE","INSPECTION_ITEM",String.valueOf(id),null);return ApiResponse.success(Collections.singletonMap("deleted",true));}
    private String role(Authentication a){return a.getAuthorities().stream().findFirst().map(x->x.getAuthority().replace("ROLE_","")).orElse("");}
    private Long userId(Authentication a){return authService.loadCurrentUser(a.getName()).getId();}
    public static class Request{@NotBlank public String facilityType;@NotBlank public String itemCode;@NotBlank public String itemName;@NotNull public Boolean requiredFlag=true;public Integer sortOrder=0;public Boolean enabled;}
}
