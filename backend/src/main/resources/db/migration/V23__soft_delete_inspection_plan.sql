ALTER TABLE inspection_plan
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER enabled;
