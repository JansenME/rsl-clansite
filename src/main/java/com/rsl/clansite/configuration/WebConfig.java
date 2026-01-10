package com.rsl.clansite.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ActivityInterceptor activityInterceptor;

    @Autowired
    public WebConfig(ActivityInterceptor activityInterceptor) {
        this.activityInterceptor = activityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register the activity tracker for all paths
        registry.addInterceptor(activityInterceptor)
                .addPathPatterns("/**") // Apply to everything
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/error", "/favicon.ico"); // Ignore static assets
    }
}
