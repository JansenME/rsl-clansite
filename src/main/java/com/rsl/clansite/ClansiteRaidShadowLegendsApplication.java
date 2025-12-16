package com.rsl.clansite;

import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.lang.management.ManagementFactory;

@SpringBootApplication
@EnableScheduling
public class ClansiteRaidShadowLegendsApplication {

	public static void main(String[] args) {
		MDC.put("process_id", String.valueOf(ManagementFactory.getRuntimeMXBean().getPid()));

		SpringApplication.run(ClansiteRaidShadowLegendsApplication.class, args);
	}

}
