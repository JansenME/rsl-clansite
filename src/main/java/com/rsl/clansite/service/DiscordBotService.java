package com.rsl.clansite.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Slf4j
@Service
public class DiscordBotService {
    @Value("${discord.bot-token}")
    private String token;

    private JDA jda;

    @PostConstruct
    public void startBot() {
        try {
            log.info("Starting Discord Bot...");
            // We need intents to see members and status
            EnumSet<GatewayIntent> intents = EnumSet.of(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_VOICE_STATES // if you want voice tracking later
            );

            jda = JDABuilder.createDefault(token)
                    .enableIntents(intents)
                    .build();

            jda.awaitReady(); // Wait until connected
            log.info("Discord Bot is ONLINE as: {}", jda.getSelfUser().getAsTag());

        } catch (Exception e) {
            log.error("Failed to start Discord Bot", e);
        }
    }

    @PreDestroy
    public void stopBot() {
        if (jda != null) {
            jda.shutdown();
            log.info("Discord Bot stopped.");
        }
    }
}
