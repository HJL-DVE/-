package com.teaclub.cloud.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源映射：把本地上传目录暴露为 {@code /static/uploads/**}。
 *
 * <p>生产环境浏览器实际走 nginx（共享同一卷），本映射用于
 * 直连 product-service（调试、小程序内网访问）时也能取到图片。
 */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    @Value("${teaclub.upload.dir:/app/uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/static/uploads/**")
                .addResourceLocations(location);
    }
}
