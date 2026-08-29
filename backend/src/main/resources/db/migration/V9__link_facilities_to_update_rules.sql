UPDATE facility f
JOIN facility_type t ON t.id=f.facility_type_id
JOIN facility_update_rule r ON r.facility_type_id=t.id AND r.enabled=1
SET f.update_rule_id=r.id
WHERE f.update_rule_id IS NULL;

ALTER TABLE facility
    ADD KEY idx_facility_update_rule (update_rule_id),
    ADD CONSTRAINT fk_facility_update_rule FOREIGN KEY (update_rule_id) REFERENCES facility_update_rule(id);
