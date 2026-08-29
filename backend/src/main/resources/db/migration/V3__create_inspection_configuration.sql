CREATE TABLE inspection_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_type_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    required_flag TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_inspection_item_code (facility_type_id,item_code),
    CONSTRAINT fk_inspection_item_type FOREIGN KEY (facility_type_id) REFERENCES facility_type(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检检查项';

CREATE TABLE inspection_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_name VARCHAR(150) NOT NULL,
    cycle_type VARCHAR(32) NOT NULL,
    facility_type_id BIGINT NULL,
    campus VARCHAR(100) NULL,
    assigned_user_id BIGINT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_plan_enabled (enabled),
    CONSTRAINT fk_plan_type FOREIGN KEY (facility_type_id) REFERENCES facility_type(id),
    CONSTRAINT fk_plan_assignee FOREIGN KEY (assigned_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_plan_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检计划';

CREATE TABLE inspection_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    facility_id BIGINT NOT NULL,
    assigned_user_id BIGINT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_task_plan_facility_due (plan_id,facility_id,due_date),
    KEY idx_task_assignee_status (assigned_user_id,status),
    CONSTRAINT fk_task_plan FOREIGN KEY (plan_id) REFERENCES inspection_plan(id),
    CONSTRAINT fk_task_facility FOREIGN KEY (facility_id) REFERENCES facility(id),
    CONSTRAINT fk_task_assignee FOREIGN KEY (assigned_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检任务';

INSERT INTO inspection_item (facility_type_id,item_code,item_name,required_flag,sort_order)
SELECT id,'APPEARANCE','外观完好、无明显破损',1,10 FROM facility_type WHERE type_code='EXTINGUISHER';
INSERT INTO inspection_item (facility_type_id,item_code,item_name,required_flag,sort_order)
SELECT id,'PRESSURE','压力表指针处于正常区域',1,20 FROM facility_type WHERE type_code='EXTINGUISHER';
INSERT INTO inspection_item (facility_type_id,item_code,item_name,required_flag,sort_order)
SELECT id,'SEAL','铅封和保险销完好',1,30 FROM facility_type WHERE type_code='EXTINGUISHER';
INSERT INTO inspection_item (facility_type_id,item_code,item_name,required_flag,sort_order)
SELECT id,'CABINET','箱门、玻璃和标识完好',1,10 FROM facility_type WHERE type_code='FIRE_HYDRANT';
INSERT INTO inspection_item (facility_type_id,item_code,item_name,required_flag,sort_order)
SELECT id,'HOSE_VALVE','水带、接口和阀门无异常',1,20 FROM facility_type WHERE type_code='FIRE_HYDRANT';

