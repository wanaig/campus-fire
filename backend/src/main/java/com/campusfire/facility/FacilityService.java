package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FacilityService {
    private final FacilityRepository repository;
    private final AuditService auditService;

    public FacilityService(FacilityRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<FacilityDtos.Summary> list(String keyword) { return repository.findAll(keyword); }
    public List<Map<String,Object>> types() { return repository.findTypes(); }
    public FacilityDtos.Summary detail(long id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("设施不存在")); }

    @Transactional
    public FacilityDtos.Summary create(FacilityDtos.CreateRequest request, long operatorId, String role) {
        requireEditor(role);
        String claimed = request.qrToken == null ? "" : request.qrToken.trim();
        try {
            long id;
            if (!claimed.isEmpty()) {
                Map<String, Object> qr = repository.findQrByToken(claimed);
                if (qr == null) throw new IllegalArgumentException("该二维码不是系统预生成的空白设施码，无法绑定");
                if (!"UNCLAIMED".equals(String.valueOf(qr.get("status"))))
                    throw new IllegalArgumentException("该二维码已绑定设施或已作废，不能重复使用");
                id = repository.insert(request, operatorId, claimed);
                repository.bindQrToken(claimed, id);
            } else {
                id = repository.insert(request, operatorId, null);
            }
            repository.applyDefaultRule(id);
            FacilityDtos.Summary facility = detail(id);
            auditService.record(operatorId, "FACILITY_CREATE", "FACILITY", String.valueOf(facility.id), details(facility));
            return facility;
        }
        catch (DuplicateKeyException e) { throw new IllegalArgumentException("设施编号已存在"); }
    }

    @Transactional
    public FacilityDtos.Summary update(FacilityDtos.UpdateRequest request, long operatorId, String role) {
        requireEditor(role);
        if(repository.update(request)==0) throw new IllegalArgumentException("设施不存在或设施类型无效");
        FacilityDtos.Summary facility = detail(request.id);
        auditService.record(operatorId, "FACILITY_UPDATE", "FACILITY", String.valueOf(request.id), details(facility));
        return facility;
    }

    @Transactional
    public FacilityDtos.Summary renewQr(long id, long operatorId, String role) { requireEditor(role); if(repository.renewQr(id)==0) throw new IllegalArgumentException("设施不存在"); repository.revokeQrBinding(id); FacilityDtos.Summary facility=detail(id); auditService.record(operatorId,"FACILITY_QR_RENEW","FACILITY",String.valueOf(id),details(facility)); return facility; }
    @Transactional
    public FacilityDtos.Summary revokeQr(long id, long operatorId, String role) { requireEditor(role); if(repository.invalidateQr(id)==0) throw new IllegalArgumentException("设施不存在"); repository.revokeQrBinding(id); FacilityDtos.Summary facility=detail(id); auditService.record(operatorId,"FACILITY_QR_REVOKE","FACILITY",String.valueOf(id),details(facility)); return facility; }

    @Transactional
    public void delete(long id, long operatorId, String role) {
        requireEditor(role);
        FacilityDtos.Summary facility = detail(id);
        for (String sql : new String[]{
                "SELECT COUNT(*) FROM inspection_task WHERE facility_id=?",
                "SELECT COUNT(*) FROM maintenance_record WHERE facility_id=?",
                "SELECT COUNT(*) FROM rectification_order WHERE facility_id=?",
                "SELECT COUNT(*) FROM facility_action_suggestion WHERE facility_id=?",
                "SELECT COUNT(*) FROM facility_lifecycle_event WHERE facility_id=?"}) {
            Integer count = repository.count(sql, id);
            if (count != null && count > 0) throw new IllegalArgumentException("该设施已有巡检或维护历史，不能删除，请改用停用/报废");
        }
        repository.detachQrBinding(id);
        if (repository.delete(id) == 0) throw new IllegalArgumentException("设施不存在");
        auditService.record(operatorId, "FACILITY_DELETE", "FACILITY", String.valueOf(id), details(facility));
    }

    private void requireEditor(String role) { if (!"ADMIN".equals(role) && !"COLLECTOR".equals(role)) throw new AccessDeniedException("当前账号无设施档案修改权限"); }

    private Map<String,Object> details(FacilityDtos.Summary facility) {
        Map<String,Object> details=new LinkedHashMap<String,Object>();
        details.put("facilityNo",facility.facilityNo); details.put("facilityType",facility.facilityType);
        details.put("lifecycleStatus",facility.lifecycleStatus); return details;
    }
}
