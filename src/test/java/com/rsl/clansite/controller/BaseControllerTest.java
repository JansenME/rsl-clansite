package com.rsl.clansite.controller;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.repository.VisitorLogRepository;
import com.rsl.clansite.security.CustomAuthenticationFailureHandler;
import com.rsl.clansite.security.CustomOAuth2UserService;
import com.rsl.clansite.security.SecurityConfig;
import com.rsl.clansite.service.AuditLogService;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.HellHadesScraperService;
import com.rsl.clansite.service.TargetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({SecurityConfig.class, BaseControllerTest.SharedTestConfig.class})
@TestPropertySource(properties = {
        "spring.session.store-type=none",
        "spring.data.mongodb.uri=mongodb://localhost",
        "discord.bot-token=dummy-token",
        "DISCORD_CLIENT_ID=dummy-id",
        "DISCORD_CLIENT_SECRET=dummy-secret"
})
public abstract class BaseControllerTest {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SessionRepository<? extends Session> sessionRepository;

    @MockitoBean
    protected ChampionsService championsService;

    @MockitoBean
    protected ClanmemberService clanmemberService;

    @MockitoBean
    protected AuditLogService auditLogService;

    @MockitoBean
    protected CommonsService commonsService;

    @MockitoBean
    protected HellHadesScraperService scraperService;

    @MockitoBean
    protected TargetService targetService;

    @MockitoBean
    protected ChampionRepository championRepository;

    @MockitoBean
    protected ClanmemberRepository clanmemberRepository;

    @MockitoBean
    protected VisitorLogRepository visitorLogRepository;

    @MockitoBean
    protected DiscordApiClient discordApiClient;

    @MockitoBean
    protected CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    protected CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    @TestConfiguration
    static class SharedTestConfig {
        @Bean
        MongoOperations mongoOperations() {
            MongoOperations mongoOps = mock(MongoOperations.class);
            IndexOperations indexOps = mock(IndexOperations.class);
            when(mongoOps.indexOps(anyString())).thenReturn(indexOps);
            return mongoOps;
        }

        @Bean
        @Primary
        public SessionRepository sessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }
    }
}
