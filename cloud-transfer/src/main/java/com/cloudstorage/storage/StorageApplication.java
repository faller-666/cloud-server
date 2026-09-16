package com.cloudstorage.storage;

import com.cloudstorage.storage.service.MinioStorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 后端 B 组——文件传输核心，最小可运行版（本地自测用）。
 *
 * 说明：正式联调时本类会被主启动模块（common 或 boot）取代，
 * 这里只为让 storage 能独立拉起服务、自测上传/下载链路而存在。
 */
@SpringBootApplication
public class StorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageApplication.class, args);
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
                System.out.println("[storage] MinIO bucket 已就绪");
            } catch (Exception e) {
                System.err.println("[storage] 警告：MinIO 不可用，bucket 未确认 —— " + e.getMessage());
            }
        };
    }
}