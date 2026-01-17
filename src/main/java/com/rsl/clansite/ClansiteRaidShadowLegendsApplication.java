package com.rsl.clansite;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;
import java.util.logging.Logger;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class ClansiteRaidShadowLegendsApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(ClansiteRaidShadowLegendsApplication.class, args);
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@EventListener(ApplicationReadyEvent.class)
	public void logActiveLogLevel() {
		if (log.isTraceEnabled()) {
			log.info("🔍 LOGGING CHECK: Application is running with TRACE level enabled.");
		} else if (log.isDebugEnabled()) {
			log.info("🔍 LOGGING CHECK: Application is running with DEBUG level enabled.");
		} else if (log.isInfoEnabled()) {
			log.info("🔍 LOGGING CHECK: Application is running with INFO level enabled.");
		} else if (log.isWarnEnabled()) {
			log.warn("🔍 LOGGING CHECK: Application is running with WARN level enabled. INFO logs will be hidden!");
		} else {
			log.error("🔍 LOGGING CHECK: Application is running with ERROR level enabled. Most logs will be hidden!");
		}
	}
}
