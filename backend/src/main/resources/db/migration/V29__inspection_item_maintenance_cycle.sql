-- 部件级保养周期：按部件生产日期＋周期计算该部件的下次保养，设施下次保养取所有部件中最早到期者
ALTER TABLE inspection_item
    ADD COLUMN maintenance_cycle_months INT NULL COMMENT '保养周期（月）：按部件生产日期自动计算下次保养，空则不按部件计算' AFTER inspection_standard;
