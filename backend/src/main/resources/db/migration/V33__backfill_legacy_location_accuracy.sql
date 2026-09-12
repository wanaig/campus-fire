-- Legacy facility coordinates were captured before accuracy was recorded.
-- Use a conservative indoor estimate until a collector captures them again.
UPDATE facility
SET location_accuracy_meters = 100
WHERE latitude IS NOT NULL
  AND longitude IS NOT NULL
  AND location_accuracy_meters IS NULL;
