package com.campusfire.inspection;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.common.api.ApiResponse;
import com.campusfire.facility.FacilityRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
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
    private final JdbcTemplate jdbcTemplate; private final AuditService auditService; private final AuthService authService; private final FacilityRepository facilityRepository;
    public InspectionItemController(JdbcTemplate jdbcTemplate, AuditService auditService, AuthService authService, FacilityRepository facilityRepository){this.jdbcTemplate=jdbcTemplate;this.auditService=auditService;this.authService=authService;this.facilityRepository=facilityRepository;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(@RequestParam String facilityType){return ApiResponse.success(jdbcTemplate.queryForList("SELECT i.id,t.type_code AS facility_type,i.item_code,i.item_name,i.inspection_standard,i.maintenance_cycle_months,i.maintenance_year_threshold,i.maintenance_cycle_before_months,i.maintenance_cycle_after_months,i.required_flag,i.sort_order,i.enabled FROM inspection_item i JOIN facility_type t ON t.id=i.facility_type_id WHERE t.type_code=? ORDER BY i.sort_order,i.id",facilityType));}
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody Request r, Authentication a){String role=role(a);if(!"ADMIN".equals(role))throw new AccessDeniedException("仅管理员可配置巡检检查项");validateMaintenanceRule(r);Long typeId=jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?",Long.class,r.facilityType);if(typeId==null)throw new IllegalArgumentException("设施类型不存在");Integer sortOrder=r.sortOrder==null?jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sort_order),0)+10 FROM inspection_item WHERE facility_type_id=?",Integer.class,typeId):r.sortOrder;String itemCode=nextItemCode(typeId);jdbcTemplate.update("INSERT INTO inspection_item(facility_type_id,item_code,item_name,inspection_standard,maintenance_cycle_months,maintenance_year_threshold,maintenance_cycle_before_months,maintenance_cycle_after_months,required_flag,sort_order) VALUES(?,?,?,?,?,?,?,?,?,?)",typeId,itemCode,r.itemName,standard(r),r.maintenanceCycleMonths,r.maintenanceYearThreshold,r.maintenanceCycleBeforeMonths,r.maintenanceCycleAfterMonths,r.requiredFlag,sortOrder);facilityRepository.refreshNextMaintenanceByType(typeId);auditService.record(userId(a),"INSPECTION_ITEM_CREATE","INSPECTION_ITEM",itemCode,null);return ApiResponse.success(Collections.singletonMap("itemCode",itemCode));}
    /** 检查项编号由系统自动生成：同类型内按 001 起的三位数字递增，不依赖前端传入 */
    private String nextItemCode(Long typeId){Integer next=jdbcTemplate.queryForObject("SELECT COALESCE(MAX(CAST(item_code AS UNSIGNED)),0)+1 FROM inspection_item WHERE facility_type_id=? AND item_code REGEXP '^[0-9]+$'",Integer.class,typeId);return String.format("%03d",next);}
    /** 拖拽排序：按前端传入的 id 顺序重写 sort_order（10、20、30…），与列表展示顺序保持一致 */
    @PutMapping("/order") @Transactional
    public ApiResponse<?> reorder(@Valid @RequestBody OrderRequest r, Authentication a) {
        String role = role(a);
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("仅管理员可配置巡检检查项");
        if (r.ids == null || r.ids.isEmpty()) throw new IllegalArgumentException("请提供检查项顺序");
        for (int i = 0; i < r.ids.size(); i++) {
            int updated = jdbcTemplate.update("UPDATE inspection_item SET sort_order=? WHERE id=?", (i + 1) * 10, r.ids.get(i));
            if (updated == 0) throw new IllegalArgumentException("检查项不存在");
        }
        auditService.record(userId(a), "INSPECTION_ITEM_REORDER", "INSPECTION_ITEM", null, Collections.singletonMap("count", r.ids.size()));
        return ApiResponse.success(Collections.singletonMap("updated", r.ids.size()));
    }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable long id,@Valid @RequestBody Request r,Authentication a){String role=role(a);if(!"ADMIN".equals(role))throw new AccessDeniedException("仅管理员可配置巡检检查项");validateMaintenanceRule(r);List<Map<String,Object>> found=jdbcTemplate.queryForList("SELECT item_code,facility_type_id,maintenance_cycle_months,maintenance_year_threshold,maintenance_cycle_before_months,maintenance_cycle_after_months FROM inspection_item WHERE id=?",id);if(found.isEmpty())throw new IllegalArgumentException("检查项不存在");String itemCode=String.valueOf(found.get(0).get("item_code"));Long oldTypeId=((Number)found.get(0).get("facility_type_id")).longValue();Long typeId=jdbcTemplate.queryForObject("SELECT id FROM facility_type WHERE type_code=?",Long.class,r.facilityType);if(typeId==null)throw new IllegalArgumentException("设施类型不存在");jdbcTemplate.update("UPDATE inspection_item SET facility_type_id=?,item_name=?,inspection_standard=?,maintenance_cycle_months=?,maintenance_year_threshold=?,maintenance_cycle_before_months=?,maintenance_cycle_after_months=?,required_flag=?,sort_order=COALESCE(?,sort_order),enabled=? WHERE id=?",typeId,r.itemName,standard(r),r.maintenanceCycleMonths,r.maintenanceYearThreshold,r.maintenanceCycleBeforeMonths,r.maintenanceCycleAfterMonths,r.requiredFlag,r.sortOrder,r.enabled==null||r.enabled?1:0,id);boolean cycleChanged=!java.util.Objects.equals(found.get(0).get("maintenance_cycle_months"),r.maintenanceCycleMonths)||!java.util.Objects.equals(found.get(0).get("maintenance_year_threshold"),r.maintenanceYearThreshold)||!java.util.Objects.equals(found.get(0).get("maintenance_cycle_before_months"),r.maintenanceCycleBeforeMonths)||!java.util.Objects.equals(found.get(0).get("maintenance_cycle_after_months"),r.maintenanceCycleAfterMonths);if(cycleChanged||!typeId.equals(oldTypeId)){facilityRepository.refreshNextMaintenanceByType(typeId);if(!typeId.equals(oldTypeId))facilityRepository.refreshNextMaintenanceByType(oldTypeId);}auditService.record(userId(a),"INSPECTION_ITEM_UPDATE","INSPECTION_ITEM",itemCode,null);return ApiResponse.success(Collections.singletonMap("updated",true));}
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable long id,Authentication a){String role=role(a);if(!"ADMIN".equals(role))throw new AccessDeniedException("仅管理员可配置巡检检查项");int n=jdbcTemplate.update("DELETE FROM inspection_item WHERE id=?",id);if(n==0)throw new IllegalArgumentException("检查项不存在");auditService.record(userId(a),"INSPECTION_ITEM_DELETE","INSPECTION_ITEM",String.valueOf(id),null);return ApiResponse.success(Collections.singletonMap("deleted",true));}
    private String role(Authentication a){return a.getAuthorities().stream().findFirst().map(x->x.getAuthority().replace("ROLE_","")).orElse("");}
    private Long userId(Authentication a){return authService.loadCurrentUser(a.getName()).getId();}
    private String standard(Request r){return r.inspectionStandard==null?"":r.inspectionStandard.trim();}
    private void validateMaintenanceRule(Request r){
        if(r.maintenanceYearThreshold==null){
            if(r.maintenanceCycleBeforeMonths!=null||r.maintenanceCycleAfterMonths!=null)throw new IllegalArgumentException("按年份配置时请填写分界年份");
            if(r.maintenanceCycleMonths!=null&&r.maintenanceCycleMonths<1)throw new IllegalArgumentException("保养周期必须大于 0");
            return;
        }
        if(r.maintenanceYearThreshold<1900||r.maintenanceYearThreshold>9999)throw new IllegalArgumentException("分界年份需为四位年份");
        if(r.maintenanceCycleMonths!=null)throw new IllegalArgumentException("固定周期和按年份周期不能同时设置");
        if(r.maintenanceCycleBeforeMonths==null||r.maintenanceCycleBeforeMonths<1||r.maintenanceCycleAfterMonths==null||r.maintenanceCycleAfterMonths<1)throw new IllegalArgumentException("请完整填写分界年份前后的保养周期");
    }
    public static class Request{@NotBlank public String facilityType;@NotBlank public String itemName;public String inspectionStandard;public Integer maintenanceCycleMonths;public Integer maintenanceYearThreshold;public Integer maintenanceCycleBeforeMonths;public Integer maintenanceCycleAfterMonths;@NotNull public Boolean requiredFlag=true;public Integer sortOrder;public Boolean enabled;}
    public static class OrderRequest{public List<Long> ids;}
}
