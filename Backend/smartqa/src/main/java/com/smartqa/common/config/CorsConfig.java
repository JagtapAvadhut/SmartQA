package com.smartqa.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class CorsConfig implements WebFluxConfigurer {

    private final SmartQaProperties properties;

    public CorsConfig(SmartQaProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = trim(properties.getCors().getAllowedOrigins().split(","));
        boolean hasPattern = false;
        for (String origin : origins) {
            if (origin.contains("*")) {
                hasPattern = true;
                break;
            }
        }
        var mapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type", "X-SmartQA-Trace-Id")
                .allowCredentials(false);
        if (hasPattern) {
            mapping.allowedOriginPatterns(origins);
        } else {
            mapping.allowedOrigins(origins).allowedOriginPatterns("http://localhost:*");
        }
    }

    private static String[] trim(String[] origins) {
        String[] trimmed = new String[origins.length];
        for (int i = 0; i < origins.length; i++) {
            trimmed[i] = origins[i].trim();
        }
        return trimmed;
    }
}
