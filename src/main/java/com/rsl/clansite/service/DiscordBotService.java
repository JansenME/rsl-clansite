package com.rsl.clansite.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Slf4j
@Service
public class DiscordBotService extends ListenerAdapter {
    @Value("${discord.bot-token}")
    private String token;

    private JDA jda;

    @PostConstruct
    public void startBot() {
        try {
            log.info("Starting Discord Bot...");
            EnumSet<GatewayIntent> intents = EnumSet.of(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES
            );

            jda = JDABuilder.createDefault(token)
                    .enableIntents(intents)
                    .addEventListeners(this)
                    .build();

            jda.awaitReady(); // Wait until connected
            log.info("Discord Bot is ONLINE as: {}", jda.getSelfUser().getAsTag());

            jda.updateCommands().addCommands(
                    Commands.slash("hello", "I will help with anything you want!")
            ).queue();
        } catch (Exception e) {
            log.error("Failed to start Discord Bot", e);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("hello")) {
            event.reply("Fuck off!")
                    .setEphemeral(false)
                    .flatMap(v ->
                            event.getHook().editOriginalFormat("Fuck off!")
                    ).queue();
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
