package com.campusfire.report;

import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import com.campusfire.auth.*; import com.campusfire.audit.AuditService; import org.springframework.security.access.AccessDeniedException; import org.springframework.security.core.Authentication;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/reports")
public class ReportExportController {
    private final JdbcTemplate jdbcTemplate; private final AuthService authService; private final AuditService auditService; private final com.campusfire.inspection.InspectionResultSupport resultSupport;
    public ReportExportController(JdbcTemplate jdbcTemplate,AuthService authService,AuditService auditService,com.campusfire.inspection.InspectionResultSupport resultSupport){this.jdbcTemplate=jdbcTemplate;this.authService=authService;this.auditService=auditService;this.resultSupport=resultSupport;}
    @GetMapping("/inspection-records.csv")
    public ResponseEntity<byte[]> inspectionRecords(Authentication authentication){
        AuthenticatedUser user=requireAdmin(authentication); auditService.record(user.getId(),"REPORT_EXPORT","INSPECTION_RECORD",null,Collections.singletonMap("format","csv"));
        List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT f.facility_no,f.name,f.campus,f.building,f.floor,f.area,f.id AS facility_id,u.display_name AS inspector,r.submitted_at,r.photo_count,r.note,r.results_json FROM inspection_record r JOIN inspection_task t ON t.id=r.task_id JOIN facility f ON f.id=t.facility_id JOIN app_user u ON u.id=r.user_id LEFT JOIN inspection_record_correction c ON c.record_id=r.id WHERE c.id IS NULL ORDER BY r.submitted_at DESC");
        java.util.Map<Long,java.util.LinkedHashMap<String,String>> facilityLabels=resultSupport.loadFacilityLabels(rows.stream().map(r->((Number)r.get("facility_id")).longValue()).collect(java.util.stream.Collectors.toSet()));
        StringBuilder csv=new StringBuilder("设施编号,设施名称,校区,楼栋,楼层,区域,巡检人员,提交时间,检查结果,照片数量,备注\r\n");
        for(Map<String,Object> row:rows){
            java.util.LinkedHashMap<String,String> labels=facilityLabels.getOrDefault(((Number)row.get("facility_id")).longValue(),new java.util.LinkedHashMap<>());
            csv.append(String.join(",",Arrays.asList(row.get("facility_no"),row.get("name"),row.get("campus"),row.get("building"),row.get("floor"),row.get("area"),row.get("inspector"),row.get("submitted_at"),resultSummary(row.get("results_json"),labels),row.get("photo_count"),row.get("note")).stream().map(v->escape(v==null?"":String.valueOf(v))).collect(java.util.stream.Collectors.toList()))).append("\r\n");}
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=inspection-records.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(("\ufeff"+csv).getBytes(StandardCharsets.UTF_8));
    }
    /** 检查结果列：异常时按检查项名称列出，例如「水带异常、枪头异常」 */
    private String resultSummary(Object resultsJson,Map<String,String> labels){
        Map<String,String> results=resultSupport.parseResults(resultsJson);
        if(results.isEmpty())return "未记录";
        List<String> abnormal=new ArrayList<>();
        for(Map.Entry<String,String> entry:results.entrySet()){
            if("FAIL".equals(entry.getValue()))abnormal.add(labels.getOrDefault(entry.getKey(),entry.getKey())+"异常");}
        return abnormal.isEmpty()?"全部正常":String.join("、",abnormal);
    }
    @GetMapping("/facilities.csv") public ResponseEntity<byte[]> facilities(Authentication a){AuthenticatedUser u=requireAdmin(a);List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT f.facility_no,f.name,t.type_name,f.campus,f.building,f.floor,f.area,f.lifecycle_status,f.manufacture_date,f.commissioned_date,f.next_maintenance_at FROM facility f JOIN facility_type t ON t.id=f.facility_type_id ORDER BY f.id");StringBuilder c=new StringBuilder("设施编号,设施名称,类型,校区,楼栋,楼层,区域,状态,生产日期,投用日期,下次保养\r\n");for(Map<String,Object> r:rows)c.append(String.join(",",Arrays.asList("facility_no","name","type_name","campus","building","floor","area","lifecycle_status","manufacture_date","commissioned_date","next_maintenance_at").stream().map(k->escape(String.valueOf(r.getOrDefault(k,"")))).collect(java.util.stream.Collectors.toList()))).append("\r\n");auditService.record(u.getId(),"REPORT_EXPORT","FACILITY",null,Collections.singletonMap("format","csv"));return csv(c,"facilities.csv");}
    @GetMapping("/rectifications.csv") public ResponseEntity<byte[]> rectifications(Authentication a){AuthenticatedUser u=requireAdmin(a);List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT f.facility_no,f.name,r.issue_summary,r.status,r.due_date,r.resolution_note,r.created_at,r.resolved_at FROM rectification_order r JOIN facility f ON f.id=r.facility_id ORDER BY r.created_at DESC");StringBuilder c=new StringBuilder("设施编号,设施名称,异常内容,状态,整改期限,整改结果,创建时间,完成时间\r\n");for(Map<String,Object> r:rows)c.append(String.join(",",Arrays.asList("facility_no","name","issue_summary","status","due_date","resolution_note","created_at","resolved_at").stream().map(k->escape(String.valueOf(r.getOrDefault(k,"")))).collect(java.util.stream.Collectors.toList()))).append("\r\n");auditService.record(u.getId(),"REPORT_EXPORT","RECTIFICATION",null,Collections.singletonMap("format","csv"));return csv(c,"rectifications.csv");}
    private ResponseEntity<byte[]> csv(StringBuilder c,String name){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+name).contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(("\ufeff"+c).getBytes(StandardCharsets.UTF_8));}
    private AuthenticatedUser requireAdmin(Authentication a){AuthenticatedUser u=authService.loadCurrentUser(a.getName());if(!"ADMIN".equals(u.getRoleCode()))throw new AccessDeniedException("仅管理员可以导出报表");return u;}
    private String escape(String value){return "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";}
}
