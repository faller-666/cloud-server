package com.cloudstorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 云存储平台 · 后端A组：认证与用户管理
 * 启动入口：IDEA 中直接运行本类即可（Redis + PostgreSQL 需先就绪）
 */
@SpringBootApplication
public class CloudStorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudStorageApplication.class, args);
    }
}