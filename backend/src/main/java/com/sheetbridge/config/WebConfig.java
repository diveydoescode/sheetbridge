package com.sheetbridge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(SheetBridgeProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final SheetBridgeProperties properties;

    public WebConfig(SheetBridgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        Arrays.stream(properties.getCorsOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(origins::add);
        origins.add("http://localhost:*");
        origins.add("http://127.0.0.1:*");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Actor");
    }
}
