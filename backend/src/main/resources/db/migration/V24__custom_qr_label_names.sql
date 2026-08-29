-- Allow each generated QR code to use a human-readable name instead of only a floor sequence.
ALTER TABLE facility_qr_code
    ADD COLUMN location_label VARCHAR(120) NULL AFTER location_no;
