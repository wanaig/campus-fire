package com.campusfire.inspection;

import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import com.campusfire.storage.RustFsStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/inspection")
public class InspectionPhotoController {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final PhotoWatermarkService watermarkService;
    private final RustFsStorageService rustFsStorage;
    private final Path storageRoot;

    public InspectionPhotoController(JdbcTemplate jdbcTemplate, AuthService authService, PhotoWatermarkService watermarkService,
                                     RustFsStorageService rustFsStorage,
                                     @Value("${app.inspection-photo.storage-path:../data/inspection-photos}") String storagePath) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.watermarkService = watermarkService;
        this.rustFsStorage = rustFsStorage;
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @GetMapping("/sessions/{sessionId}/photo-prompts")
    public ApiResponse<?> prompts(@PathVariable String sessionId, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        Map<String, Object> session = loadSession(sessionId, user.getId());
        String type = jdbcTemplate.queryForObject(
                "SELECT ft.type_code FROM inspection_session s JOIN facility f ON f.id=COALESCE(s.facility_id,(SELECT t.facility_id FROM inspection_task t WHERE t.id=s.task_id)) JOIN facility_type ft ON ft.id=f.facility_type_id WHERE s.id=?",
                String.class, sessionId);
        List<String> candidates;
        if ("FIRE_HYDRANT".equals(type)) {
            candidates = Arrays.asList("消防栓整体与箱门标识", "水带、接口和阀门", "周边通道无遮挡");
        } else if ("EXTINGUISHER".equals(type)) {
            candidates = Arrays.asList("灭火器整体与摆放位置", "压力表和铅封", "瓶体铭牌与有效期");
        } else {
            candidates = Arrays.asList("器材整体与安装位置", "铭牌和有效期", "周边通道无遮挡");
        }
        List<String> selected = new ArrayList<>(candidates);
        Collections.shuffle(selected);
        return ApiResponse.success(Collections.singletonMap("prompts", selected.subList(0, Math.min(2, selected.size()))));
    }

    /** 管理端查看巡检留痕照片：已登录用户可读取；按记录的存储后端分流（RUSTFS 对象存储 / LOCAL 本地磁盘，本地路径校验防止越界） */
    @GetMapping("/photos/{photoId}/file")
    public ResponseEntity<byte[]> file(@PathVariable String photoId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT storage_path,storage_backend,content_type FROM inspection_photo WHERE id=?", photoId);
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

    @PostMapping(value = "/sessions/{sessionId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@PathVariable String sessionId,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam("captureSource") String captureSource,
                                 @RequestParam("clientCapturedAt") Instant clientCapturedAt,
                                 @RequestParam("deviceId") String deviceId,
                                 @RequestParam("latitude") BigDecimal latitude,
                                 @RequestParam("longitude") BigDecimal longitude,
                                 @RequestParam(value = "accuracyMeters", required = false) BigDecimal accuracyMeters,
                                 Authentication authentication) {
        AuthenticatedUser user = requireGuard(authentication);
        Map<String, Object> session = loadSession(sessionId, user.getId());
        validateMetadata(file, captureSource, clientCapturedAt, deviceId);
        validateLocation(session, latitude, longitude, accuracyMeters);

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
                watermarkLines(session, latitude, longitude, accuracyMeters, serverReceivedAt));
        String sha256 = sha256(bytes);
        String watermarkSha256 = sha256(watermarkedBytes);

        if (rustFsStorage.isEnabled()) {
            saveToRustFs(session, user, photoId, sessionId, file, bytes, watermarkedBytes, extension,
                    captureSource, clientCapturedAt, deviceId, latitude, longitude, accuracyMeters,
                    sha256, watermarkSha256, serverReceivedAt);
            return ApiResponse.success(new PhotoResponse(photoId, sha256, serverReceivedAt));
        }

        saveToLocalDisk(session, user, photoId, sessionId, file, bytes, watermarkedBytes, extension,
                captureSource, clientCapturedAt, deviceId, latitude, longitude, accuracyMeters,
                sha256, watermarkSha256, serverReceivedAt);
        return ApiResponse.success(new PhotoResponse(photoId, sha256, serverReceivedAt));
    }

    /** 照片写入 RustFS 对象存储：对象 key 为 inspection-photos/{sessionId}/{photoId}.jpg（原图带 .original 后缀留痕） */
    private void saveToRustFs(Map<String, Object> session, AuthenticatedUser user, String photoId, String sessionId,
                              MultipartFile file, byte[] bytes, byte[] watermarkedBytes, String extension,
                              String captureSource, Instant clientCapturedAt, String deviceId,
                              BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                              String sha256, String watermarkSha256, Instant serverReceivedAt) {
        String key = "inspection-photos/" + sessionId + "/" + photoId + extension;
        String originalKey = "inspection-photos/" + sessionId + "/" + photoId + ".original" + extension;
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
            insertPhotoRecord(photoId, sessionId, session, user, key, originalKey, "RUSTFS", file, bytes,
                    sha256, watermarkSha256, captureSource, clientCapturedAt, deviceId,
                    latitude, longitude, accuracyMeters);
        } catch (RuntimeException e) {
            rustFsStorage.deleteObjectQuietly(originalKey);
            rustFsStorage.deleteObjectQuietly(key);
            throw e;
        }
    }

    /** 照片写入服务器本地磁盘（RustFS 未启用时的兜底路径，保持原有目录结构） */
    private void saveToLocalDisk(Map<String, Object> session, AuthenticatedUser user, String photoId, String sessionId,
                                 MultipartFile file, byte[] bytes, byte[] watermarkedBytes, String extension,
                                 String captureSource, Instant clientCapturedAt, String deviceId,
                                 BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                                 String sha256, String watermarkSha256, Instant serverReceivedAt) {
        Path sessionDirectory = storageRoot.resolve(sessionId).normalize();
        Path target = sessionDirectory.resolve(photoId + extension).normalize();
        Path originalTarget = sessionDirectory.resolve(photoId + ".original" + extension).normalize();
        if (!target.startsWith(storageRoot) || !originalTarget.startsWith(storageRoot)) {
            throw new IllegalArgumentException("照片存储路径无效");
        }
        try {
            Files.createDirectories(sessionDirectory);
            Files.write(originalTarget, bytes);
            Files.write(target, watermarkedBytes);
        } catch (IOException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            try { Files.deleteIfExists(originalTarget); } catch (IOException ignored) { }
            throw new IllegalArgumentException("现场照片保存失败");
        }
        try {
            insertPhotoRecord(photoId, sessionId, session, user, target.toString(), originalTarget.toString(), "LOCAL",
                    file, bytes, sha256, watermarkSha256, captureSource, clientCapturedAt, deviceId,
                    latitude, longitude, accuracyMeters);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            try { Files.deleteIfExists(originalTarget); } catch (IOException ignored) { }
            throw e;
        }
    }

    private void insertPhotoRecord(String photoId, String sessionId, Map<String, Object> session, AuthenticatedUser user,
                                   String storagePath, String originalStoragePath, String storageBackend,
                                   MultipartFile file, byte[] bytes, String sha256, String watermarkSha256,
                                   String captureSource, Instant clientCapturedAt, String deviceId,
                                   BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        jdbcTemplate.update("INSERT INTO inspection_photo(id,session_id,task_id,user_id,storage_path,original_storage_path,storage_backend,original_filename,content_type,file_size,sha256,watermark_sha256,capture_source,client_captured_at,device_id,latitude,longitude,location_accuracy_meters) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                photoId, sessionId, session.get("task_id") == null ? null : ((Number) session.get("task_id")).longValue(), user.getId(),
                storagePath, originalStoragePath, storageBackend,
                safeFilename(file.getOriginalFilename()), file.getContentType(), bytes.length, sha256, watermarkSha256,
                captureSource, java.sql.Timestamp.from(clientCapturedAt), deviceId.trim(), latitude, longitude, accuracyMeters);
    }

    private Map<String, Object> loadSession(String sessionId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.task_id,s.latitude,s.longitude,s.location_accuracy_meters,f.facility_no,f.name,f.campus,f.building,f.floor,f.area,f.detail_location,u.display_name " +
                        "FROM inspection_session s LEFT JOIN inspection_task t ON t.id=s.task_id " +
                        "JOIN facility f ON f.id=COALESCE(s.facility_id,t.facility_id) JOIN app_user u ON u.id=s.user_id " +
                        "WHERE s.id=? AND s.user_id=? AND s.status='STARTED'",
                sessionId, userId);
        if (rows.isEmpty()) throw new AccessDeniedException("巡检会话无效或不属于当前账号");
        return rows.get(0);
    }

    /** 巡检留痕照片仅限保安账号上传：管理员/采集员不能向巡检会话写照片 */
    private AuthenticatedUser requireGuard(Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        if (!"GUARD".equals(user.getRoleCode())) throw new AccessDeniedException("仅保安账号可以执行巡检操作");
        return user;
    }

    private void validateMetadata(MultipartFile file, String captureSource, Instant clientCapturedAt, String deviceId) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("必须上传现场照片");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("现场照片不能超过10MB");
        if (!"CAMERA".equals(captureSource)) throw new IllegalArgumentException("只允许使用现场相机拍摄，不能从相册选择");
        if (deviceId == null || deviceId.trim().isEmpty()) throw new IllegalArgumentException("缺少拍摄设备标识");
        Instant now = Instant.now();
        if (clientCapturedAt == null || clientCapturedAt.isBefore(now.minus(Duration.ofMinutes(10)))
                || clientCapturedAt.isAfter(now.plus(Duration.ofMinutes(2)))) {
            throw new IllegalArgumentException("照片拍摄时间无效，请现场重新拍摄");
        }
    }

    private void validateLocation(Map<String, Object> session, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        double distance = LocationProximity.distanceMeters((Number) session.get("latitude"), (Number) session.get("longitude"), latitude, longitude);
        double allowedDistance = LocationProximity.allowedDistanceMeters((Number) session.get("location_accuracy_meters"), accuracyMeters);
        if (distance > allowedDistance) {
            throw new IllegalArgumentException(String.format("照片定位距离巡检起点约%d米（本次允许%d米），请重新定位后拍摄", Math.round(distance), Math.round(allowedDistance)));
        }
    }

    private List<String> watermarkLines(Map<String, Object> session, BigDecimal latitude, BigDecimal longitude,
                                        BigDecimal accuracyMeters, Instant serverReceivedAt) {
        String facility = String.valueOf(session.get("facility_no")) + " · " + String.valueOf(session.get("name"));
        List<String> locationParts = new ArrayList<>();
        for (String key : Arrays.asList("campus", "building", "floor", "area", "detail_location")) {
            Object value = session.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) locationParts.add(String.valueOf(value).trim());
        }
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Shanghai")).format(serverReceivedAt);
        String accuracy = accuracyMeters == null ? "精度未知" : "精度约±" + Math.round(accuracyMeters.doubleValue()) + "米";
        return Arrays.asList(
                "设施：" + facility,
                "位置：" + String.join(" / ", locationParts),
                "巡检：" + String.valueOf(session.get("display_name")) + " · 时间：" + time,
                String.format(java.util.Locale.ROOT, "GPS：%.6f, %.6f · %s", latitude.doubleValue(), longitude.doubleValue(), accuracy));
    }

    private String imageExtension(MultipartFile file) {
        if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(file.getContentType())) return ".jpg";
        if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(file.getContentType())) return ".png";
        throw new IllegalArgumentException("现场照片仅支持 JPEG 或 PNG 格式");
    }

    private void validateImageSignature(byte[] bytes, String extension) {
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        boolean png = bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        if ((".jpg".equals(extension) && !jpeg) || (".png".equals(extension) && !png)) {
            throw new IllegalArgumentException("上传文件不是有效的现场照片");
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
