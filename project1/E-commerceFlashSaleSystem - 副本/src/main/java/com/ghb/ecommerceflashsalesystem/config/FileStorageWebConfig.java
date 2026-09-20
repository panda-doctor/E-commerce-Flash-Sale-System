package com.ghb.ecommerceflashsalesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 本地图片静态资源映射：/uploads/** -> {storage.local.dir}/（本地存储策略的读取出口）
 *
 * 注意：路径基于应用运行目录（如 mvn spring-boot:run 的工程根目录）解析为绝对路径，
 * 与 LocalImageStorageService 的落盘目录保持一致。
 */
@Configuration
public class FileStorageWebConfig implements WebMvcConfigurer {

    @Value("${storage.local.dir:uploads}")
    private String storageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(storageDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
