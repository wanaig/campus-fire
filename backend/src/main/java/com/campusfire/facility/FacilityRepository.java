package com.campusfire.facility;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FacilityRepository {
    private final JdbcTemplate jdbcTemplate;

    public FacilityRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    private final String select = "SELECT f.id, f.facility_no, t.type_code, f.name, f.campus, f.building, " +
            "f.floor, f.area, f.detail_location, f.latitude, f.longitude, f.qr_token, f.brand, f.model, " +
            "f.specification, f.manufacture_date, f.commissioned_date, f.lifecycle_status, f.update_rule_id, " +
            "f.next_maintenance_at, DATE_ADD(COALESCE(f.commissioned_date,f.manufacture_date), INTERVAL r.service_life_years YEAR) AS expected_update_date " +
            "FROM facility f JOIN facility_type t ON t.id=f.facility_type_id LEFT JOIN facility_update_rule r ON r.id=f.update_rule_id ";

    public long insert(FacilityDtos.CreateRequest r, long creatorId, String qrToken) {
        KeyHolder holder = new GeneratedKeyHolder();
        // 设施编号是系统唯一标识，由后端生成：前端传空时先用占位值插入，再按自增 ID 回填「F+6位」编号
        String provided = r.facilityNo == null ? "" : r.facilityNo.trim();
        String facilityNo = provided.isEmpty() ? "AUTO-" + UUID.randomUUID().toString().replace("-", "") : provided;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO facility " +
                    "(facility_no, facility_type_id, name, campus, building, floor, area, detail_location, " +
                    "latitude, longitude, qr_token, brand, model, specification, manufacture_date, commissioned_date, lifecycle_status, created_by) " +
                    "SELECT ?, id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM facility_type WHERE type_code=?",
                    Statement.RETURN_GENERATED_KEYS);
            int i=1; ps.setString(i++,facilityNo); ps.setString(i++,r.name); ps.setString(i++,r.campus); ps.setString(i++,r.building);
            ps.setString(i++,r.floor); ps.setString(i++,r.area); ps.setString(i++,r.detailLocation); ps.setBigDecimal(i++,r.latitude); ps.setBigDecimal(i++,r.longitude);
            ps.setString(i++,qrToken != null ? qrToken : UUID.randomUUID().toString().replace("-", "")); ps.setString(i++,r.brand); ps.setString(i++,r.model); ps.setString(i++,r.specification);
            ps.setObject(i++,r.manufactureDate); ps.setObject(i++,r.commissionedDate); ps.setString(i++,r.lifecycleStatus); ps.setLong(i++,creatorId); ps.setString(i,r.facilityType); return ps;
        }, holder);
        long id = holder.getKey().longValue();
        if (provided.isEmpty()) jdbcTemplate.update("UPDATE facility SET facility_no=? WHERE id=?", String.format("F%06d", id), id);
        return id;
    }

    public Map<String, Object> findQrByToken(String token) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,serial_no,status,facility_id FROM facility_qr_code WHERE token=?", token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int bindQrToken(String token, long facilityId) {
        return jdbcTemplate.update("UPDATE facility_qr_code SET status='BOUND',facility_id=?,bound_at=NOW() " +
                "WHERE token=? AND status='UNCLAIMED'", facilityId, token);
    }

    public int revokeQrBinding(long facilityId) {
        return jdbcTemplate.update("UPDATE facility_qr_code SET status='REVOKED' WHERE facility_id=? AND status='BOUND'", facilityId);
    }

    public int detachQrBinding(long facilityId) {
        return jdbcTemplate.update("UPDATE facility_qr_code SET status='REVOKED',facility_id=NULL,bound_at=NULL WHERE facility_id=?", facilityId);
    }

    public void applyDefaultRule(long facilityId) {
        jdbcTemplate.update("UPDATE facility f JOIN facility_update_rule r ON r.facility_type_id=f.facility_type_id AND r.enabled=1 SET f.update_rule_id=r.id, f.next_maintenance_at=CASE WHEN r.maintenance_cycle_months IS NOT NULL AND COALESCE(f.commissioned_date,f.manufacture_date) IS NOT NULL THEN DATE_ADD(COALESCE(f.commissioned_date,f.manufacture_date), INTERVAL r.maintenance_cycle_months MONTH) ELSE f.next_maintenance_at END WHERE f.id=? AND f.update_rule_id IS NULL", facilityId);
    }

    public int update(FacilityDtos.UpdateRequest r) {
        return jdbcTemplate.update("UPDATE facility f JOIN facility_type t ON t.type_code=? LEFT JOIN facility_update_rule r ON r.facility_type_id=t.id AND r.enabled=1 SET f.name=?, f.campus=?, f.building=?, f.floor=?, f.area=?, f.detail_location=?, f.latitude=?, f.longitude=?, f.brand=?, f.model=?, f.specification=?, f.manufacture_date=?, f.commissioned_date=?, f.lifecycle_status=?, f.facility_type_id=t.id, f.update_rule_id=COALESCE(f.update_rule_id,r.id), f.next_maintenance_at=CASE WHEN f.next_maintenance_at IS NULL AND r.maintenance_cycle_months IS NOT NULL THEN DATE_ADD(COALESCE(?,?), INTERVAL r.maintenance_cycle_months MONTH) ELSE f.next_maintenance_at END WHERE f.id=?",
                r.facilityType,r.name,r.campus,r.building,r.floor,r.area,r.detailLocation,r.latitude,r.longitude,r.brand,r.model,r.specification,r.manufactureDate,r.commissionedDate,r.lifecycleStatus,r.commissionedDate,r.manufactureDate,r.id);
    }

    public Optional<FacilityDtos.Summary> findById(long id) { return query(select+"WHERE f.id=?", id).stream().findFirst(); }
    public List<FacilityDtos.Summary> findAll(String keyword) {
        String like = keyword == null ? "" : "%"+keyword+"%";
        return query(select+"WHERE (?='' OR f.facility_no LIKE ? OR f.name LIKE ? OR f.detail_location LIKE ?) ORDER BY f.id DESC", like,like,like,like);
    }
    public int invalidateQr(long id) { return jdbcTemplate.update("UPDATE facility SET qr_token=? WHERE id=?", "REVOKED-"+UUID.randomUUID(), id); }
    public int renewQr(long id) { return jdbcTemplate.update("UPDATE facility SET qr_token=? WHERE id=?", UUID.randomUUID().toString().replace("-", ""), id); }
    public Integer count(String sql, long id) { return jdbcTemplate.queryForObject(sql, Integer.class, id); }
    public int delete(long id) { return jdbcTemplate.update("DELETE FROM facility WHERE id=?", id); }
    public List<Map<String,Object>> findTypes() { return jdbcTemplate.queryForList("SELECT type_code AS typeCode,type_name AS typeName FROM facility_type WHERE enabled=1 ORDER BY id"); }

    public Map<String, String> findItemNames(String typeCode) {
        return jdbcTemplate.query(
                "SELECT i.item_code,i.item_name FROM inspection_item i JOIN facility_type t ON t.id=i.facility_type_id " +
                        "WHERE t.type_code=? AND i.enabled=1 ORDER BY i.sort_order,i.id",
                rs -> {
                    Map<String, String> names = new LinkedHashMap<>();
                    while (rs.next()) names.put(rs.getString("item_code"), rs.getString("item_name"));
                    return names;
                }, typeCode);
    }

    public void deleteComponents(long facilityId) {
        jdbcTemplate.update("DELETE FROM facility_component WHERE facility_id=?", facilityId);
    }

    public void insertComponent(long facilityId, String itemCode, String itemName, java.time.LocalDate manufactureDate) {
        jdbcTemplate.update("INSERT INTO facility_component(facility_id,item_code,item_name,manufacture_date) VALUES(?,?,?,?)",
                facilityId, itemCode, itemName, manufactureDate);
    }

    public List<FacilityDtos.Component> findComponents(long facilityId) {
        return jdbcTemplate.query("SELECT item_code,item_name,manufacture_date FROM facility_component WHERE facility_id=? ORDER BY id",
                (rs, n) -> {
                    FacilityDtos.Component c = new FacilityDtos.Component();
                    c.itemCode = rs.getString("item_code");
                    c.itemName = rs.getString("item_name");
                    if (rs.getDate("manufacture_date") != null) c.manufactureDate = rs.getDate("manufacture_date").toLocalDate();
                    return c;
                }, facilityId);
    }

    private List<FacilityDtos.Summary> query(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs,n)-> { FacilityDtos.Summary s=new FacilityDtos.Summary(); s.id=rs.getLong("id"); s.facilityNo=rs.getString("facility_no"); s.facilityType=rs.getString("type_code"); s.name=rs.getString("name"); s.campus=rs.getString("campus"); s.building=rs.getString("building"); s.floor=rs.getString("floor"); s.area=rs.getString("area"); s.detailLocation=rs.getString("detail_location"); s.latitude=rs.getBigDecimal("latitude"); s.longitude=rs.getBigDecimal("longitude"); s.qrToken=rs.getString("qr_token"); s.brand=rs.getString("brand"); s.model=rs.getString("model"); s.specification=rs.getString("specification"); if(rs.getDate("manufacture_date")!=null)s.manufactureDate=rs.getDate("manufacture_date").toLocalDate(); if(rs.getDate("commissioned_date")!=null)s.commissionedDate=rs.getDate("commissioned_date").toLocalDate(); s.lifecycleStatus=rs.getString("lifecycle_status"); s.updateRuleId=rs.getObject("update_rule_id",Long.class); if(rs.getDate("expected_update_date")!=null)s.expectedUpdateDate=rs.getDate("expected_update_date").toLocalDate(); if(rs.getTimestamp("next_maintenance_at")!=null)s.nextMaintenanceAt=rs.getTimestamp("next_maintenance_at").toLocalDateTime(); return s; }, args);
    }
}
