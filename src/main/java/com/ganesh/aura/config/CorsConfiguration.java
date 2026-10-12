package com.ganesh.aura.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfiguration implements WebMvcConfigurer {

    private final String frontendOrigin;

    public CorsConfiguration(@Value("${frontend.origin}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/calls/chat")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type");
    }
}
