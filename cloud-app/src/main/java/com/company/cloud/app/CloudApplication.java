package com.company.cloud.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 私有云存储平台启动类。
 *
 * <p>扫描全部业务模块（auth / transfer / files）。双实例部署：
 * 实例一 --server.port=3001，实例二 --server.port=3002，Nginx 反代负载。
 */
@SpringBootApplication(scanBasePackages = "com.company.cloud")
@MapperScan("com.company.cloud.**.mapper")
public class CloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudApplication.class, args);
    }
}
