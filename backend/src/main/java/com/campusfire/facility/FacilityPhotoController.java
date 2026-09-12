package com.campusfire.facility;

import com.campusfire.audit.AuditService;
import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import com.campusfire.inspection.LocationProximity;
import com.campusfire.inspection.PhotoWatermarkService;
import com.campusfire.storage.RustFsStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 采集员设施初始照片留痕：与保安巡检照片同一套防造假约束（仅现场相机、拍摄时间窗口、
 * JPEG/PNG 文件签名、SHA-256 哈希、原图+水印双存档），仅角色门槛不同——设施档案可由
 * 采集员（和管理员）维护，上传以 facilityId 为锚点而非巡检会话。
 */
@RestController
public class FacilityPhotoController {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final PhotoWatermarkService watermarkService;
    private final RustFsStorageService rustFsStorage;
    private final AuditService auditService;
    private final Path storageRoot;

    public FacilityPhotoController(JdbcTemplate jdbcTemplate, AuthService authService, PhotoWatermarkService watermarkService,
                                   RustFsStorageService rustFsStorage, AuditService auditService,
                                   @Value("${app.inspection-photo.storage-path:../data/inspection-photos}") String storagePath) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.watermarkService = watermarkService;
        this.rustFsStorage = rustFsStorage;
        this.auditService = auditService;
        // 与巡检照片共用同一存储根目录，facility-photos 子目录隔离，本地兜底路径校验可复用
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    /** 设施初始照片清单：照片清单与文件接口只读公开，访客扫码查询设施时可直接查看 */
    @GetMapping("/facilities/{facilityId}/photos")
    public ApiResponse<?> list(@PathVariable long facilityId) {
        requireFacility(facilityId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id, p.client_captured_at, u.display_name AS uploaded_by FROM facility_photo p " +
                        "JOIN app_user u ON u.id=p.user_id WHERE p.facility_id=? ORDER BY p.client_captured_at", facilityId);
        List<Map<String, Object>> photos = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> photo = new LinkedHashMap<>();
            photo.put("photoId", String.valueOf(row.get("id")));
            photo.put("capturedAt", row.get("client_captured_at"));
            photo.put("uploadedBy", row.get("uploaded_by"));
            photos.add(photo);
        }
        return ApiResponse.success(photos);
    }

    @PostMapping(value = "/facilities/{facilityId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@PathVariable long facilityId,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam("captureSource") String captureSource,
                                 @RequestParam("clientCapturedAt") Instant clientCapturedAt,
                                 @RequestParam("deviceId") String deviceId,
                                 @RequestParam("latitude") BigDecimal latitude,
                                 @RequestParam("longitude") BigDecimal longitude,
                                 @RequestParam(value = "accuracyMeters", required = false) BigDecimal accuracyMeters,
                                 Authentication authentication) {
        AuthenticatedUser user = requireEditor(authentication);
        Map<String, Object> facility = requireFacility(facilityId);
        validateMetadata(file, captureSource, clientCapturedAt, deviceId);
        validateLocation(facility, latitude, longitude, accuracyMeters);

        String photoId = UUID.randomUUID().toString();
        String extension = imageExtension(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取照片内容失败");
        }
        validateImageSignature(bytes, extension);
        Instant serverReceivedAt = Instant.now();
        byte[] watermarkedBytes = watermarkService.addWatermark(bytes, extension.substring(1),
                watermarkLines(facility, user, latitude, longitude, accuracyMeters, serverReceivedAt));
        String sha256 = sha256(bytes);
        String watermarkSha256 = sha256(watermarkedBytes);

        if (rustFsStorage.isEnabled()) {
            saveToRustFs(facilityId, user, photoId, file, bytes, watermarkedBytes, extension,
                    captureSource, clientCapturedAt, deviceId, latitude, longitude, accuracyMeters,
                    sha256, watermarkSha256);
        } else {
            saveToLocalDisk(facilityId, user, photoId, file, bytes, watermarkedBytes, extension,
                    captureSource, clientCapturedAt, deviceId, latitude, longitude, accuracyMeters,
                    sha256, watermarkSha256);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("facilityNo", String.valueOf(facility.get("facility_no")));
        details.put("sha256", sha256);
        details.put("fileSize", bytes.length);
        auditService.record(user.getId(), "FACILITY_PHOTO_UPLOAD", "FACILITY", String.valueOf(facilityId), details);
        return ApiResponse.success(new PhotoResponse(photoId, sha256, serverReceivedAt));
    }

    /** 按存储后端分流读取水印图（RUSTFS 对象存储 / LOCAL 本地磁盘，本地路径校验防止越界） */
    @GetMapping("/facility-photos/{photoId}/file")
    public ResponseEntity<byte[]> file(@PathVariable String photoId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT storage_path,storage_backend,content_type FROM facility_photo WHERE id=?", photoId);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> row = rows.get(0);
        String backend = row.get("storage_backend") == null ? "LOCAL" : String.valueOf(row.get("storage_backend"));
        MediaType contentType = MediaType.parseMediaType(String.valueOf(row.get("content_type")));
        try {
            byte[] bytes;
            if ("RUSTFS".equals(backend)) {
                bytes = rustFsStorage.getObject(String.valueOf(row.get("storage_path")));
            } else {
                Path target = Paths.get(String.valueOf(row.get("storage_path"))).toAbsolutePath().normalize();
                if (!target.startsWith(storageRoot)) return ResponseEntity.notFound().build();
                bytes = Files.readAllBytes(target);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .contentType(contentType)
                    .body(bytes);
        } catch (IOException | RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** 采集员保存档案时移除不需要的初始照片：数据库行与存储文件一并清理 */
    @DeleteMapping("/facility-photos/{photoId}")
    public ApiResponse<?> delete(@PathVariable String photoId, Authentication authentication) {
        AuthenticatedUser user = requireEditor(authentication);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,facility_id,storage_path,original_storage_path,storage_backend FROM facility_photo WHERE id=?", photoId);
        if (rows.isEmpty()) throw new IllegalArgumentException("照片不存在或已被删除");
        Map<String, Object> row = rows.get(0);
        jdbcTemplate.update("DELETE FROM facility_photo WHERE id=?", photoId);
        cleanupStorage(row);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("facilityId", ((Number) row.get("facility_id")).longValue());
        auditService.record(user.getId(), "FACILITY_PHOTO_DELETE", "FACILITY", String.valueOf(row.get("facility_id")), details);
        return ApiResponse.success(java.util.Collections.singletonMap("deleted", true));
    }

    /** 照片写入 RustFS 对象存储：对象 key 为 facility-photos/{facilityId}/{photoId}.jpg（原图带 .original 后缀留痕） */
    private void saveToRustFs(long facilityId, AuthenticatedUser user, String photoId,
                              MultipartFile file, byte[] bytes, byte[] watermarkedBytes, String extension,
                              String captureSource, Instant clientCapturedAt, String deviceId,
                              BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                              String sha256, String watermarkSha256) {
        String key = "facility-photos/" + facilityId + "/" + photoId + extension;
        String originalKey = "facility-photos/" + facilityId + "/" + photoId + ".original" + extension;
        String contentType = file.getContentType();
        try {
            rustFsStorage.putObject(originalKey, bytes, contentType);
            rustFsStorage.putObject(key, watermarkedBytes, contentType);
        } catch (RuntimeException e) {
            rustFsStorage.deleteObjectQuietly(originalKey);
            rustFsStorage.deleteObjectQuietly(key);
            throw new IllegalArgumentException("照片上传到对象存储失败，请确认 RustFS 服务可用后重试");
        }
        try {
            insertPhotoRecord(photoId, facilityId, user, key, originalKey, "RUSTFS", file, bytes,
                    sha256, watermarkSha256, captureSource, clientCapturedAt, deviceId, latitude, longitude, accuracyMeters);
        } catch (RuntimeException e) {
            rustFsStorage.deleteObjectQuietly(originalKey);
            rustFsStorage.deleteObjectQuietly(key);
            throw e;
        }
    }

    /** 照片写入服务器本地磁盘（RustFS 未启用时的兜底路径）：{storageRoot}/facility-photos/{facilityId}/ */
    private void saveToLocalDisk(long facilityId, AuthenticatedUser user, String photoId,
                                 MultipartFile file, byte[] bytes, byte[] watermarkedBytes, String extension,
                                 String captureSource, Instant clientCapturedAt, String deviceId,
                                 BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                                 String sha256, String watermarkSha256) {
        Path facilityDirectory = storageRoot.resolve("facility-photos").resolve(String.valueOf(facilityId)).normalize();
        Path target = facilityDirectory.resolve(photoId + extension).normalize();
        Path originalTarget = facilityDirectory.resolve(photoId + ".original" + extension).normalize();
        if (!target.startsWith(storageRoot) || !originalTarget.startsWith(storageRoot)) {
            throw new IllegalArgumentException("照片存储路径无效");
        }
        try {
            Files.createDirectories(facilityDirectory);
            Files.write(originalTarget, bytes);
            Files.write(target, watermarkedBytes);
        } catch (IOException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            try { Files.deleteIfExists(originalTarget); } catch (IOException ignored) { }
            throw new IllegalArgumentException("设施照片保存失败");
        }
        try {
            insertPhotoRecord(photoId, facilityId, user, target.toString(), originalTarget.toString(), "LOCAL",
                    file, bytes, sha256, watermarkSha256, captureSource, clientCapturedAt, deviceId, latitude, longitude, accuracyMeters);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            try { Files.deleteIfExists(originalTarget); } catch (IOException ignored) { }
            throw e;
        }
    }

    private void insertPhotoRecord(String photoId, long facilityId, AuthenticatedUser user,
                                   String storagePath, String originalStoragePath, String storageBackend,
                                   MultipartFile file, byte[] bytes, String sha256, String watermarkSha256,
                                   String captureSource, Instant clientCapturedAt, String deviceId,
                                   BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        jdbcTemplate.update("INSERT INTO facility_photo(id,facility_id,user_id,storage_path,original_storage_path,storage_backend,original_filename,content_type,file_size,sha256,watermark_sha256,capture_source,client_captured_at,device_id,latitude,longitude,location_accuracy_meters) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                photoId, facilityId, user.getId(), storagePath, originalStoragePath, storageBackend,
                safeFilename(file.getOriginalFilename()), file.getContentType(), bytes.length, sha256, watermarkSha256,
                captureSource, java.sql.Timestamp.from(clientCapturedAt), deviceId.trim(), latitude, longitude, accuracyMeters);
    }

    private void cleanupStorage(Map<String, Object> row) {
        String backend = row.get("storage_backend") == null ? "LOCAL" : String.valueOf(row.get("storage_backend"));
        if ("RUSTFS".equals(backend)) {
            rustFsStorage.deleteObjectQuietly(String.valueOf(row.get("storage_path")));
            rustFsStorage.deleteObjectQuietly(row.get("original_storage_path") == null ? null : String.valueOf(row.get("original_storage_path")));
        } else {
            deleteLocalFile(String.valueOf(row.get("storage_path")));
            deleteLocalFile(row.get("original_storage_path") == null ? null : String.valueOf(row.get("original_storage_path")));
        }
    }

    private void deleteLocalFile(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (target.startsWith(storageRoot)) Files.deleteIfExists(target);
        } catch (IOException | RuntimeException ignored) { }
    }

    private Map<String, Object> requireFacility(long facilityId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT facility_no,name,campus,building,floor,area,detail_location,latitude,longitude,location_accuracy_meters " +
                        "FROM facility WHERE id=?", facilityId);
        if (rows.isEmpty()) throw new IllegalArgumentException("设施不存在");
        return rows.get(0);
    }

    /** 设施初始照片仅限采集员/管理员上传：保安与访客不能改动设施档案留痕 */
    private AuthenticatedUser requireEditor(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"ADMIN".equals(user.getRoleCode()) && !"COLLECTOR".equals(user.getRoleCode())) {
            throw new AccessDeniedException("仅采集员账号可以维护设施档案");
        }
        return user;
    }

    private void validateMetadata(MultipartFile file, String captureSource, Instant clientCapturedAt, String deviceId) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("必须上传设施照片");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("设施照片不能超过10MB");
        if (!"CAMERA".equals(captureSource)) throw new IllegalArgumentException("只允许使用现场相机拍摄，不能从相册选择");
        if (deviceId == null || deviceId.trim().isEmpty()) throw new IllegalArgumentException("缺少拍摄设备标识");
        Instant now = Instant.now();
        // 建档照片随表单一起在保存时上传，窗口放宽到 30 分钟（巡检照片为 10 分钟）
        if (clientCapturedAt == null || clientCapturedAt.isBefore(now.minus(Duration.ofMinutes(30)))
                || clientCapturedAt.isAfter(now.plus(Duration.ofMinutes(2)))) {
            throw new IllegalArgumentException("照片拍摄时间过久，请现场重新拍摄");
        }
    }

    /** 照片定位与设施档案坐标比对（老档案坐标缺失时跳过距离校验，仅记录 GPS） */
    private void validateLocation(Map<String, Object> facility, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        if (facility.get("latitude") == null || facility.get("longitude") == null) return;
        double distance = LocationProximity.distanceMeters((Number) facility.get("latitude"), (Number) facility.get("longitude"), latitude, longitude);
        double allowedDistance = LocationProximity.allowedDistanceMeters((Number) facility.get("location_accuracy_meters"), accuracyMeters);
        if (distance > allowedDistance) {
            throw new IllegalArgumentException(String.format("照片定位距离设施位置约%d米（本次允许%d米），请站在设施旁重新拍摄", Math.round(distance), Math.round(allowedDistance)));
        }
    }

    private List<String> watermarkLines(Map<String, Object> facility, AuthenticatedUser user,
                                        BigDecimal latitude, BigDecimal longitude,
                                        BigDecimal accuracyMeters, Instant serverReceivedAt) {
        String facilityLine = String.valueOf(facility.get("facility_no")) + " · " + String.valueOf(facility.get("name"));
        List<String> locationParts = new java.util.ArrayList<>();
        for (String key : Arrays.asList("campus", "building", "floor", "area", "detail_location")) {
            Object value = facility.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) locationParts.add(String.valueOf(value).trim());
        }
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Shanghai")).format(serverReceivedAt);
        String accuracy = accuracyMeters == null ? "精度未知" : "精度约±" + Math.round(accuracyMeters.doubleValue()) + "米";
        return Arrays.asList(
                "设施：" + facilityLine,
                "位置：" + String.join(" / ", locationParts),
                "采集：" + String.valueOf(user.getDisplayName()) + " · 时间：" + time,
                String.format(java.util.Locale.ROOT, "GPS：%.6f, %.6f · %s", latitude.doubleValue(), longitude.doubleValue(), accuracy));
    }

    private String imageExtension(MultipartFile file) {
        if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(file.getContentType())) return ".jpg";
        if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(file.getContentType())) return ".png";
        throw new IllegalArgumentException("设施照片仅支持 JPEG 或 PNG 格式");
    }

    private void validateImageSignature(byte[] bytes, String extension) {
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        boolean png = bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        if ((".jpg".equals(extension) && !jpeg) || (".png".equals(extension) && !png)) {
            throw new IllegalArgumentException("上传文件不是有效的设施照片");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte b : digest) value.append(String.format("%02x", b));
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("系统不支持照片哈希计算", e);
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "camera-photo";
        return Paths.get(filename).getFileName().toString();
    }

    public static class PhotoResponse {
        public String photoId;
        public String sha256;
        public Instant serverReceivedAt;
        PhotoResponse(String photoId, String sha256, Instant serverReceivedAt) {
            this.photoId = photoId;
            this.sha256 = sha256;
            this.serverReceivedAt = serverReceivedAt;
        }
    }
}
