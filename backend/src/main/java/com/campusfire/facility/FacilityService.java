package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.storage.PhotoStorageCleaner;
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
    private final PhotoStorageCleaner photoStorageCleaner;

    public FacilityService(FacilityRepository repository, AuditService auditService, PhotoStorageCleaner photoStorageCleaner) {
        this.repository = repository;
        this.auditService = auditService;
        this.photoStorageCleaner = photoStorageCleaner;
    }

    public List<FacilityDtos.Summary> list(String keyword) { return repository.findAll(keyword); }
    public List<Map<String,Object>> types() { return repository.findTypes(); }
    public FacilityDtos.Summary detail(long id) {
        FacilityDtos.Summary facility = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("设施不存在"));
        facility.components = repository.findComponents(id);
        return facility;
    }

    @Transactional
    public FacilityDtos.Summary create(FacilityDtos.CreateRequest request, long operatorId, String role) {
        requireEditor(role);
        // 一物一码：设施档案只能由采集员扫描预生成的空白二维码标签建档并绑定该码，不允许无码单独新增
        String claimed = request.qrToken == null ? "" : request.qrToken.trim();
        if (claimed.isEmpty()) throw new IllegalArgumentException("请扫描设施上预生成的空白二维码标签建档，设施必须一物一码");
        try {
            Map<String, Object> qr = repository.findQrByToken(claimed);
            if (qr == null) throw new IllegalArgumentException("该二维码不是系统预生成的空白设施码，无法绑定");
            if (!"UNCLAIMED".equals(String.valueOf(qr.get("status"))))
                throw new IllegalArgumentException("该二维码已绑定设施，不能重复使用");
            long id = repository.insert(request, operatorId, claimed);
            if (repository.bindQrToken(claimed, id) == 0) throw new IllegalArgumentException("该二维码刚被其他设施占用，请重新扫码");
            repository.applyDefaultRule(id);
            saveComponents(id, request.facilityType, request.components);
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
        // 管理端编辑不传 components（null），保持已登记部件不变；小程序编辑总是传完整列表
        if (request.components != null) saveComponents(request.id, request.facilityType, request.components);
        FacilityDtos.Summary facility = detail(request.id);
        auditService.record(operatorId, "FACILITY_UPDATE", "FACILITY", String.valueOf(request.id), details(facility));
        return facility;
    }

    /** 保存建档勾选的部件清单：部件必须属于该类型的启用检查项，生产日期选填 */
    private void saveComponents(long facilityId, String facilityType, List<FacilityDtos.ComponentInput> components) {
        repository.deleteComponents(facilityId);
        if (components != null && !components.isEmpty()) {
            Map<String, String> itemNames = repository.findItemNames(facilityType);
            for (FacilityDtos.ComponentInput component : components) {
                if (component == null || component.itemCode == null || component.itemCode.trim().isEmpty()) continue;
                String code = component.itemCode.trim();
                String name = itemNames.get(code);
                if (name == null) throw new IllegalArgumentException("部件「" + code + "」不属于该设施类型的检查项，请刷新后重试");
                repository.insertComponent(facilityId, code, name, component.manufactureDate);
            }
        }
        // 部件清单或生产日期变化后按部件重算下次保养（无部件周期时回退设施级保养规则）
        repository.refreshNextMaintenanceFromComponents(facilityId);
    }

    @Transactional
    public FacilityDtos.Summary renewQr(long id, long operatorId, String role) { requireEditor(role); if(repository.renewQr(id)==0) throw new IllegalArgumentException("设施不存在"); repository.revokeQrBinding(id); FacilityDtos.Summary facility=detail(id); auditService.record(operatorId,"FACILITY_QR_RENEW","FACILITY",String.valueOf(id),details(facility)); return facility; }
    @Transactional
    public FacilityDtos.Summary revokeQr(long id, long operatorId, String role) { requireEditor(role); if(repository.invalidateQr(id)==0) throw new IllegalArgumentException("设施不存在"); repository.revokeQrBinding(id); FacilityDtos.Summary facility=detail(id); auditService.record(operatorId,"FACILITY_QR_REVOKE","FACILITY",String.valueOf(id),details(facility)); return facility; }

    /**
     * 删除设施档案：同步级联删除该设施全部关联数据（巡检记录/照片/会话、整改单、维护记录、二维码绑定等）。
     * force 参数仅为兼容旧前端保留，当前行为始终级联（测试阶段要求删除设施时同步清理巡检数据）。
     */
    @Transactional
    public void delete(long id, long operatorId, String role, boolean force) {
        requireEditor(role);
        FacilityDtos.Summary facility = detail(id);
        cascadeDeleteFacilityData(id);
        // 设施初始照片（含外键约束）：删档案前先清照片行与存储文件（尽力而为）
        photoStorageCleaner.deleteQuietly(repository.findRows(
                "SELECT id,storage_path,original_storage_path,storage_backend FROM facility_photo WHERE facility_id=?", id));
        repository.update("DELETE FROM facility_photo WHERE facility_id=?", id);
        repository.detachQrBinding(id);
        if (repository.delete(id) == 0) throw new IllegalArgumentException("设施不存在");
        auditService.record(operatorId, "FACILITY_DELETE", "FACILITY", String.valueOf(id), details(facility));
    }

    /** 强制删除设施的全部关联数据；顺序遵循外键依赖（被引用表先删），V25 前的遗留 task 链路一并覆盖 */
    private void cascadeDeleteFacilityData(long id) {
        String sessions = "(SELECT id FROM inspection_session WHERE facility_id=? OR task_id IN (SELECT id FROM inspection_task WHERE facility_id=?))";
        String tasks = "(SELECT id FROM inspection_task WHERE facility_id=?)";
        String records = "(SELECT id FROM inspection_record WHERE facility_id=? OR session_id IN " + sessions + " OR task_id IN " + tasks + ")";
        // 先取出照片记录用于清理 RustFS 对象/本地文件（尽力而为）
        photoStorageCleaner.deleteQuietly(repository.findRows(
                "SELECT id,storage_path,original_storage_path,storage_backend FROM inspection_photo WHERE session_id IN " + sessions + " OR task_id IN " + tasks,
                id, id, id));
        repository.update("DELETE FROM inspection_photo WHERE session_id IN " + sessions + " OR task_id IN " + tasks, id, id, id);
        repository.update("DELETE FROM inspection_record_correction WHERE record_id IN " + records, id, id, id, id);
        repository.update("DELETE FROM inspection_risk_flag WHERE session_id IN " + sessions, id, id);
        repository.update("DELETE FROM rectification_order WHERE facility_id=? OR record_id IN " + records + " OR task_id IN " + tasks, id, id, id, id, id, id);
        repository.update("DELETE FROM inspection_record WHERE facility_id=? OR session_id IN " + sessions + " OR task_id IN " + tasks, id, id, id, id);
        repository.update("DELETE FROM inspection_draft WHERE session_id IN " + sessions, id, id);
        repository.update("DELETE FROM inspection_session WHERE facility_id=? OR task_id IN " + tasks, id, id);
        repository.update("DELETE FROM inspection_task WHERE facility_id=?", id);
        repository.update("DELETE FROM maintenance_record WHERE facility_id=?", id);
        repository.update("DELETE FROM facility_action_suggestion WHERE facility_id=?", id);
        repository.update("DELETE FROM facility_lifecycle_event WHERE facility_id=?", id);
    }

    private void requireEditor(String role) { if (!"ADMIN".equals(role) && !"COLLECTOR".equals(role)) throw new AccessDeniedException("当前账号无设施档案修改权限"); }

    private Map<String,Object> details(FacilityDtos.Summary facility) {
        Map<String,Object> details=new LinkedHashMap<String,Object>();
        details.put("facilityNo",facility.facilityNo); details.put("facilityType",facility.facilityType);
        details.put("lifecycleStatus",facility.lifecycleStatus); return details;
    }
}
