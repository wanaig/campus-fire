-- Persist the full label hierarchy and an immutable sequence within each floor.
ALTER TABLE facility_qr_code
    ADD COLUMN school VARCHAR(100) NOT NULL DEFAULT '湖南科技职业学院' AFTER serial_no,
    ADD COLUMN location_no INT NULL AFTER floor;

UPDATE facility_qr_code q
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY school, campus, building, floor
               ORDER BY serial_no
           ) AS generated_location_no
    FROM facility_qr_code
) ranked ON ranked.id = q.id
SET q.location_no = ranked.generated_location_no
WHERE q.campus IS NOT NULL
  AND q.building IS NOT NULL
  AND q.floor IS NOT NULL;

CREATE UNIQUE INDEX uk_qr_location_no
    ON facility_qr_code(school, campus, building, floor, location_no);
