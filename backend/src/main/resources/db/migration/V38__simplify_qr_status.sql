-- 二维码状态简化：只保留 未绑定(UNCLAIMED)/已绑定(BOUND)/已删除(DELETED) 三种；
-- 存量已作废(REVOKED)的二维码统一恢复为未绑定，可重新扫码绑定设施。
UPDATE facility_qr_code SET status='UNCLAIMED', facility_id=NULL, bound_at=NULL WHERE status='REVOKED';
