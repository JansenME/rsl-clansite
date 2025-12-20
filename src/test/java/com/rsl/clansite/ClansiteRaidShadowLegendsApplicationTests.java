package com.rsl.clansite;

import com.rsl.clansite.service.DiscordRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ClansiteRaidShadowLegendsApplicationTests {

	@MockitoBean
	private DiscordRoleService discordRoleService;

	@Test
	void contextLoads() {
	}

}
