package com.campusfire.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 照片存储清理：按 inspection_photo / facility_photo 行记录的存储后端删除 RustFS 对象或本地磁盘文件。
 * 仅供删除设施/巡检数据时做尽力而为的清理，任何删除失败只记日志、不阻断数据库删除。
 */
@Service
public class PhotoStorageCleaner {
    private static final Logger log = LoggerFactory.getLogger(PhotoStorageCleaner.class);

    private final RustFsStorageService rustFsStorage;
    private final Path storageRoot;

    public PhotoStorageCleaner(RustFsStorageService rustFsStorage,
                               @Value("${app.inspection-photo.storage-path:../data/inspection-photos}") String storagePath) {
        this.rustFsStorage = rustFsStorage;
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    /** 入参行需含 storage_backend、storage_path，可含 original_storage_path（列名与 inspection_photo 表一致） */
    public void deleteQuietly(List<Map<String, Object>> photoRows) {
        if (photoRows == null) return;
        for (Map<String, Object> row : photoRows) {
            String backend = row.get("storage_backend") == null ? "LOCAL" : String.valueOf(row.get("storage_backend"));
            if ("RUSTFS".equals(backend)) {
                rustFsStorage.deleteObjectQuietly(text(row.get("storage_path")));
                rustFsStorage.deleteObjectQuietly(text(row.get("original_storage_path")));
            } else {
                deleteLocalFile(text(row.get("storage_path")));
                deleteLocalFile(text(row.get("original_storage_path")));
            }
        }
    }

    private void deleteLocalFile(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (!target.startsWith(storageRoot)) return;
            Files.deleteIfExists(target);
        } catch (IOException | RuntimeException e) {
            log.warn("清理本地照片文件失败：{}，原因：{}", path, e.getMessage());
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
