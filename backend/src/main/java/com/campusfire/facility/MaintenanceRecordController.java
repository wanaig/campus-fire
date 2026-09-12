package com.campusfire.facility;

import com.campusfire.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 维护保养记录只读查询：记录由保安小程序端提交巡检时自动生成，管理端不再手动登记。
 */
@RestController
@RequestMapping("/maintenance-records")
public class MaintenanceRecordController {
    private final JdbcTemplate jdbcTemplate;
    public MaintenanceRecordController(JdbcTemplate jdbcTemplate){this.jdbcTemplate=jdbcTemplate;}
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) Long facilityId){return ApiResponse.success(jdbcTemplate.queryForList("SELECT m.*,f.facility_no,f.name,u.display_name AS creator FROM maintenance_record m JOIN facility f ON f.id=m.facility_id JOIN app_user u ON u.id=m.created_by WHERE (? IS NULL OR m.facility_id=?) ORDER BY m.maintenance_at DESC",facilityId,facilityId));}
}
