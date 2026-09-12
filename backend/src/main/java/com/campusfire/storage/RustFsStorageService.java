package com.campusfire.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * RustFS 对象存储访问服务（RustFS 兼容 S3 API，使用 AWS SDK v2 访问）。
 * 仅当 app.inspection-photo.storage=rustfs 时启用；启用后启动阶段做 bucket 连通性自检。
 */
@Service
public class RustFsStorageService {
    private static final Logger log = LoggerFactory.getLogger(RustFsStorageService.class);

    private final boolean enabled;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final String region;
    private S3Client s3;

    public RustFsStorageService(@Value("${app.inspection-photo.storage:rustfs}") String storage,
                                @Value("${app.inspection-photo.rustfs.endpoint:http://47.96.95.149:9001/rustfs}") String endpoint,
                                @Value("${app.inspection-photo.rustfs.access-key:minioadmin}") String accessKey,
                                @Value("${app.inspection-photo.rustfs.secret-key:minioadmin}") String secretKey,
                                @Value("${app.inspection-photo.rustfs.bucket:rjxy}") String bucket,
                                @Value("${app.inspection-photo.rustfs.region:us-east-1}") String region) {
        this.enabled = "rustfs".equalsIgnoreCase(storage);
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.region = region;
    }

    @PostConstruct
    void initialize() {
        if (!enabled) {
            log.info("照片对象存储未启用（app.inspection-photo.storage != rustfs），继续使用本地磁盘存储");
            return;
        }
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true)
                .build();
        try {
            ensureBucketExists();
            log.info("RustFS 对象存储已就绪：endpoint={}，bucket={}", endpoint, bucket);
        } catch (Exception e) {
            log.error("RustFS 对象存储自检失败：endpoint={}，bucket={}，原因：{}。启动继续，但照片上传/读取将失败，请检查网络、AccessKey/SecretKey 或 endpoint 配置",
                    endpoint, bucket, e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void putObject(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    public byte[] getObject(String key) {
        try (InputStream in = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return StreamUtils.copyToByteArray(in);
        } catch (IOException e) {
            throw new IllegalStateException("读取对象存储照片失败", e);
        }
    }

    /** 上传失败回滚时使用：删除已上传对象，删除失败仅记录日志，不掩盖原始异常 */
    public void deleteObjectQuietly(String key) {
        if (s3 == null || key == null) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("回滚删除 RustFS 对象失败：bucket={}，key={}，原因：{}", bucket, key, e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("RustFS bucket 不存在，已自动创建：{}", bucket);
        }
    }
}
