-- 二维码池支持按楼栋/楼层分组生成：贴码时按层分发，序号形如「楼栋-楼层-序号」
ALTER TABLE facility_qr_code
    ADD COLUMN campus VARCHAR(100) NULL AFTER serial_no,
    ADD COLUMN building VARCHAR(100) NULL AFTER campus,
    ADD COLUMN floor VARCHAR(50) NULL AFTER building;
