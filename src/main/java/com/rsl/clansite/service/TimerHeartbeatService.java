package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.EventTimerEntity;
import com.rsl.clansite.model.entity.HubConfigEntity;
import com.rsl.clansite.repository.EventTimerRepository;
import com.rsl.clansite.repository.HubConfigRepository;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class TimerHeartbeatService {

    private final EventTimerRepository eventTimerRepository;
    private final HubConfigRepository hubConfigRepository;
    private final JDA jda;

    public TimerHeartbeatService(EventTimerRepository eventTimerRepository,
                                 HubConfigRepository hubConfigRepository,
                                 JDA jda) {
        this.eventTimerRepository = eventTimerRepository;
        this.hubConfigRepository = hubConfigRepository;
        this.jda = jda;
    }

    @Scheduled(fixedDelay = 60000) // Run every 1 minute
    public void updateDiscordHub() {
        List<HubConfigEntity> configs = hubConfigRepository.findAll();

        if (configs.isEmpty()) {
            return; // No hubs configured yet across any server
        }

        List<EventTimerEntity> timers = eventTimerRepository.findAll();
        if (timers.isEmpty()) {
            return; // Nothing to show
        }

        // 1. Build the embed ONCE for all guilds to save memory and CPU
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("🛡️ Clan Cronus - Strategic Timers");
        embedBuilder.setColor(new Color(41, 128, 185)); // A nice Discord blue
        embedBuilder.setFooter("Pulse updated every minute");
        embedBuilder.setTimestamp(Instant.now());

        for (EventTimerEntity timer : timers) {
            Duration remaining = Duration.between(Instant.now(), timer.getTargetTime());
            String timeString = formatDuration(remaining);

            // We include the native Discord relative timestamp so users can hover for exact time
            String preciseTime = String.format("<t:%d:R>", timer.getTargetTime().getEpochSecond());

            embedBuilder.addField(timer.getEventName(), timeString + " (" + preciseTime + ")", false);
        }

        MessageEmbed finalEmbed = embedBuilder.build();

        // 2. Loop through all registered hubs and update them
        for (HubConfigEntity config : configs) {
            if (config.getChannelId() == null || config.getMessageId() == null) {
                continue;
            }

            TextChannel channel = jda.getTextChannelById(config.getChannelId());
            if (channel == null) {
                log.warn("Configured Hub channel {} not found in guild {}. Check bot permissions.",
                        config.getChannelId(), config.getGuildId());
                continue;
            }

            channel.editMessageById(config.getMessageId(), "** **")
                    .setEmbeds(finalEmbed)
                    .queue(
                            success -> log.debug("Hub message updated successfully in channel {}.", config.getChannelId()),
                            error -> log.error("Failed to update Hub message in channel {}. Error: {}",
                                    config.getChannelId(), error.getMessage())
                    );
        }
    }

    private String formatDuration(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return "🔥 **HAPPENING NOW / REFRESHING**";
        }

        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m");

        return sb.toString().trim();
    }
}