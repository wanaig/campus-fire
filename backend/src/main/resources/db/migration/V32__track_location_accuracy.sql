-- Keep the accuracy reported by the positioning provider so proximity checks
-- can account for normal indoor GPS / network-location drift.
ALTER TABLE facility
    ADD COLUMN location_accuracy_meters DECIMAL(10,2) NULL AFTER longitude;

ALTER TABLE inspection_session
    ADD COLUMN location_accuracy_meters DECIMAL(10,2) NULL AFTER longitude;

ALTER TABLE inspection_photo
    ADD COLUMN location_accuracy_meters DECIMAL(10,2) NULL AFTER longitude;
