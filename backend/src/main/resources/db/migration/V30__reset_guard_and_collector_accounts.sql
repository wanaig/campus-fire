-- Preserve the business records owned by the original demo users while moving
-- them into the new numbered account scheme. The administrator is untouched.
UPDATE app_user
SET username = 'guard01',
    display_name = '保安01',
    password_hash = '$2a$10$ECRa/iASUJn9bHMDKrDueOxAuuaY4iNOsdcK4rE/GaEabfiWQocQi',
    enabled = 1
WHERE username = 'guard';

UPDATE app_user
SET username = 'collector01',
    display_name = '采集员01',
    password_hash = '$2a$10$Y6UBP/ePLn95KsTcGzX0dO4r.DgO7/uRsYY9KHe0FAXxlO95F2DL.',
    enabled = 1
WHERE username = 'collector';

INSERT INTO app_user (username, display_name, password_hash, role_id, enabled)
SELECT CONCAT('guard', LPAD(sequence_no, 2, '0')),
       CONCAT('保安', LPAD(sequence_no, 2, '0')),
       '$2a$10$ECRa/iASUJn9bHMDKrDueOxAuuaY4iNOsdcK4rE/GaEabfiWQocQi',
       role_id,
       1
FROM (
    SELECT 1 AS sequence_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) account_numbers
CROSS JOIN (SELECT id AS role_id FROM app_role WHERE role_code = 'GUARD') guard_role
WHERE NOT EXISTS (
    SELECT 1 FROM app_user existing_user
    WHERE existing_user.username = CONCAT('guard', LPAD(sequence_no, 2, '0'))
);

INSERT INTO app_user (username, display_name, password_hash, role_id, enabled)
SELECT CONCAT('collector', LPAD(sequence_no, 2, '0')),
       CONCAT('采集员', LPAD(sequence_no, 2, '0')),
       '$2a$10$Y6UBP/ePLn95KsTcGzX0dO4r.DgO7/uRsYY9KHe0FAXxlO95F2DL.',
       role_id,
       1
FROM (
    SELECT 1 AS sequence_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) account_numbers
CROSS JOIN (SELECT id AS role_id FROM app_role WHERE role_code = 'COLLECTOR') collector_role
WHERE NOT EXISTS (
    SELECT 1 FROM app_user existing_user
    WHERE existing_user.username = CONCAT('collector', LPAD(sequence_no, 2, '0'))
);
