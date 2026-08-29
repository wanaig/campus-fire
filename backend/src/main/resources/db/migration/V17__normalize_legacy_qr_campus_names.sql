-- Older labels sometimes stored the school prefix inside campus. Split it so
-- the hierarchy consistently reads school -> campus -> building -> floor.
UPDATE facility_qr_code
SET campus = TRIM(SUBSTRING(campus, CHAR_LENGTH(school) + 1))
WHERE campus LIKE CONCAT(school, '%')
  AND CHAR_LENGTH(campus) > CHAR_LENGTH(school);
