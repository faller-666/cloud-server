package com.company.cloud.auth.config;

import com.company.cloud.auth.security.RolesInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置：注册 RolesInterceptor（RolesGuard 等价实现）+ App/EXE 安装包本地静态映射
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RolesInterceptor rolesInterceptor;

    /** App/EXE 安装包落盘目录（app.update.download-dir），/downloads/** 静态映射到该目录 */
    @Value("${app.update.download-dir}")
    private String downloadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rolesInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 后端自身托管 downloads 目录，避免依赖外部 nginx：本地联调/无 nginx 场景直接可下载
        // 地址 /downloads/**（context-path=/api 时对外为 /api/downloads/**）
        String location = Paths.get(downloadDir).toUri().toString();
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations(location);
    }
}