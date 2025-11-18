package com.rsl.clansite;

import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.management.ManagementFactory;

@SpringBootApplication
public class ClansiteRaidShadowLegendsApplication {

	public static void main(String[] args) {
		MDC.put("process_id", String.valueOf(ManagementFactory.getRuntimeMXBean().getPid()));

		SpringApplication.run(ClansiteRaidShadowLegendsApplication.class, args);
	}

}
