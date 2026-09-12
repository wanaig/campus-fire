-- 照片存储后端标记：LOCAL=服务器本地磁盘，RUSTFS=RustFS 对象存储（S3 兼容 API）
-- 存量照片保持 LOCAL，从本地磁盘读取；新照片按配置写入 RustFS 并标记 RUSTFS，storage_path 存对象 key
ALTER TABLE inspection_photo
    ADD COLUMN storage_backend VARCHAR(16) NOT NULL DEFAULT 'LOCAL' AFTER original_storage_path;
