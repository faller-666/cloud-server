package com.company.cloud.transfer.config;

import com.company.cloud.transfer.service.MinioStorageService;
import io.minio.MinioAsyncClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端装配。
 * 对应任务书 CS-DOC-03 与 CS-DOC-01 的 MinIO 环境：
 * 单机模式起步，加盘后可原地扩分布式，业务代码（S3 语义）零改动。
 *
 * 注：multipart（分片/断点续传）在 minio-java 9.x 走异步客户端 MinioAsyncClient 的
 * 公开 builder API；同步对象操作（预签名等）同客户端内直接可用。
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioAsyncClient minioClient() {
        return MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 启动时确保 MinIO bucket 存在。
     * MinIO 未就绪时仅告警、不阻塞启动，方便先起来看接口。
     */
    @Bean
    CommandLineRunner ensureStorageBucket(MinioStorageService minio) {
        return args -> {
            try {
                minio.ensureBucket();
                System.out.println("[transfer] MinIO bucket 已就绪");
            } catch (Exception e) {
                System.err.println("[transfer] 警告：MinIO 不可用，bucket 未确认 —— " + e.getMessage());
            }
        };
    }
}