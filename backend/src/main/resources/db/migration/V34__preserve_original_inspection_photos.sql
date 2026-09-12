-- The displayed file is watermarked; retain the untouched upload and hashes for audit evidence.
ALTER TABLE inspection_photo
    ADD COLUMN original_storage_path VARCHAR(512) NULL AFTER storage_path,
    ADD COLUMN watermark_sha256 VARCHAR(64) NULL AFTER sha256;

UPDATE inspection_photo
SET original_storage_path = storage_path
WHERE original_storage_path IS NULL;
