-- Keep deleted QR records visible and recoverable from the administration UI.
ALTER TABLE facility_qr_code
    ADD COLUMN deleted_at DATETIME NULL AFTER bound_at;

CREATE INDEX idx_qr_deleted_at ON facility_qr_code(deleted_at);
