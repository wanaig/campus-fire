package com.campusfire.inspection;

import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
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
    private final Path storageRoot;

    public InspectionPhotoController(JdbcTemplate jdbcTemplate, AuthService authService,
                                     @Value("${app.inspection-photo.storage-path:../data/inspection-photos}") String storagePath) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @GetMapping("/sessions/{sessionId}/photo-prompts")
    public ApiResponse<?> prompts(@PathVariable String sessionId, Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        Map<String, Object> session = loadSession(sessionId, user.getId());
        String type = jdbcTemplate.queryForObject(
                "SELECT ft.type_code FROM inspection_session s JOIN inspection_task t ON t.id=s.task_id JOIN facility f ON f.id=t.facility_id JOIN facility_type ft ON ft.id=f.facility_type_id WHERE s.id=?",
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

    /** 管理端查看巡检留痕照片：已登录用户可读取，路径校验防止越界 */
    @GetMapping("/photos/{photoId}/file")
    public ResponseEntity<byte[]> file(@PathVariable String photoId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT storage_path,content_type FROM inspection_photo WHERE id=?", photoId);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        Path target = Paths.get(String.valueOf(rows.get(0).get("storage_path"))).toAbsolutePath().normalize();
        if (!target.startsWith(storageRoot)) return ResponseEntity.notFound().build();
        try {
            byte[] bytes = Files.readAllBytes(target);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .contentType(MediaType.parseMediaType(String.valueOf(rows.get(0).get("content_type"))))
                    .body(bytes);
        } catch (IOException e) {
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
                                 Authentication authentication) {
        AuthenticatedUser user = authService.loadCurrentUser(authentication.getName());
        Map<String, Object> session = loadSession(sessionId, user.getId());
        validateMetadata(file, captureSource, clientCapturedAt, deviceId);
        validateLocation(session, latitude, longitude);

        String photoId = UUID.randomUUID().toString();
        String extension = imageExtension(file);
        Path sessionDirectory = storageRoot.resolve(sessionId).normalize();
        Path target = sessionDirectory.resolve(photoId + extension).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("照片存储路径无效");
        }

        try {
            byte[] bytes = file.getBytes();
            validateImageSignature(bytes, extension);
            Files.createDirectories(sessionDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String sha256 = sha256(bytes);
            jdbcTemplate.update("INSERT INTO inspection_photo(id,session_id,task_id,user_id,storage_path,original_filename,content_type,file_size,sha256,capture_source,client_captured_at,device_id,latitude,longitude) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    photoId, sessionId, ((Number) session.get("task_id")).longValue(), user.getId(), target.toString(),
                    safeFilename(file.getOriginalFilename()), file.getContentType(), file.getSize(), sha256,
                    captureSource, java.sql.Timestamp.from(clientCapturedAt), deviceId.trim(), latitude, longitude);
            return ApiResponse.success(new PhotoResponse(photoId, sha256, Instant.now()));
        } catch (IOException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new IllegalArgumentException("现场照片保存失败");
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw e;
        }
    }

    private Map<String, Object> loadSession(String sessionId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT task_id,latitude,longitude FROM inspection_session WHERE id=? AND user_id=? AND status='STARTED'",
                sessionId, userId);
        if (rows.isEmpty()) throw new AccessDeniedException("巡检会话无效或不属于当前账号");
        return rows.get(0);
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

    private void validateLocation(Map<String, Object> session, BigDecimal latitude, BigDecimal longitude) {
        double expectedLat = ((Number) session.get("latitude")).doubleValue();
        double expectedLon = ((Number) session.get("longitude")).doubleValue();
        double dLat = expectedLat - latitude.doubleValue();
        double dLon = (expectedLon - longitude.doubleValue()) * Math.cos(Math.toRadians(latitude.doubleValue()));
        if (Math.sqrt(dLat * dLat + dLon * dLon) * 111000 > 100) {
            throw new IllegalArgumentException("照片定位与巡检现场不一致，请在设施附近重新拍摄");
        }
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
