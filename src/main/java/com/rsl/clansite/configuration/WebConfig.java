package com.rsl.clansite.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.storage.location.champion-cards}")
    private String storageLocation;

    private final ActivityInterceptor activityInterceptor;

    @Autowired
    public WebConfig(ActivityInterceptor activityInterceptor) {
        this.activityInterceptor = activityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activityInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/error", "/favicon.ico");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = Paths.get(storageLocation).toAbsolutePath().normalize();
        String resourcePath = path.toUri().toString();

        registry.addResourceHandler("/images/champion-cards/**")
                .addResourceLocations(resourcePath);
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> increaseTomcatFormLimit() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            // Force the limit to 10,000 parameters
            connector.setProperty("maxParameterCount", "10000");
            // Also increase the max POST size to 10MB just in case
            connector.setMaxPostSize(10 * 1024 * 1024);
        });
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> logTomcatSettings(WebServerApplicationContext context) {
        return event -> {
            if (context.getWebServer() instanceof TomcatWebServer tomcatWebServer) {
                tomcatWebServer.getTomcat().getService().findConnectors()[0].getProperty("maxParameterCount");
                int maxParams = (int) tomcatWebServer.getTomcat().getConnector().getProperty("maxParameterCount");
                log.error("\n=================================================");
                log.error("🔴 TOMCAT DEBUG: Max Parameter Count is: " + maxParams);
                log.error("=================================================\n");
            }
        };
    }
}
