package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.HubConfigEntity;
import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.repository.HubConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent; // Added Import
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DiscordBotService extends ListenerAdapter {

    @Value("${discord.bot-token}")
    private String token;

    private JDA jda;
    private final SiegeService siegeService;
    private final HubConfigRepository hubConfigRepository;

    public DiscordBotService(SiegeService siegeService, HubConfigRepository hubConfigRepository) {
        this.siegeService = siegeService;
        this.hubConfigRepository = hubConfigRepository;
    }

    @PostConstruct
    public void startBot() {
        try {
            log.info("Starting Discord Bot asynchronously...");
            EnumSet<GatewayIntent> intents = EnumSet.of(
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES
            );

            // We build the bot but DO NOT wait for it to be ready.
            // This prevents the Spring Boot startup from freezing if Discord is down.
            jda = JDABuilder.createDefault(token)
                    .enableIntents(intents)
                    .addEventListeners(this)
                    .build();

            // Note: Command registration is moved to onReady() below

        } catch (Exception e) {
            log.error("Failed to start Discord Bot", e);
        }
    }

    /**
     * This event fires when the Discord Bot has successfully connected and is ready.
     * We perform our API calls (like registering commands) here to ensure stability.
     */
    @Override
    public void onReady(ReadyEvent event) {
        log.info("Discord Bot is ONLINE as: {}", event.getJDA().getSelfUser().getAsTag());

        event.getJDA().updateCommands().addCommands(
                Commands.slash("hello", "I will help with anything you want!"),
                Commands.slash("robot", "What am I?"),
                Commands.slash("eventhub", "Initialize the Event Timer Hub in this channel")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                Commands.slash("siege-score", "Get the current Siege Score (Battle/Finished)")
                        .addOptions(new OptionData(OptionType.STRING, "tier", "Select T1 or T2 manually")
                                .addChoice("T1", "T1")
                                .addChoice("T2", "T2"))
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
        ).queue();
    }

    @Bean
    public JDA jdaClient() {
        return this.jda;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        if (commandName.equals("hello")) {
            event.reply("Fuck off!")
                    .setEphemeral(false)
                    .queue();
        } else if (commandName.equals("robot")) {
            event.reply("Beep Baap Boop! I... AM... A... ROBOT...")
                    .setEphemeral(false)
                    .queue();
        } else if (commandName.equals("siege-score")) {
            handleSiegeScoreCommand(event);
        } else if (commandName.equals("eventhub")) {
            handleEventHubCommand(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.equals("siege_score_t1")) {
            event.reply(formatSiegeScore(ClanGroup.T1)).setEphemeral(false).queue();
        } else if (componentId.equals("siege_score_t2")) {
            event.reply(formatSiegeScore(ClanGroup.T2)).setEphemeral(false).queue();
        } else if (componentId.equals("hub_move")) {
            handleMoveHub(event);
        } else if (componentId.equals("hub_keep")) {
            handleKeepHub(event);
        } else if (componentId.equals("hub_cancel")) {
            event.editMessage("Hub setup cancelled.").setComponents().queue();
        }
    }

    private void handleEventHubCommand(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel)) {
            event.reply("This command can only be used in a standard text channel.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = (TextChannel) event.getChannel();
        String guildId = event.getGuild().getId();
        String channelId = channel.getId();

        Optional<HubConfigEntity> existingInChannel = hubConfigRepository.findByGuildIdAndChannelId(guildId, channelId);

        // Scenario B: Hub already exists in THIS channel
        if (existingInChannel.isPresent()) {
            HubConfigEntity config = existingInChannel.get();

            // Try to delete old message silently
            channel.deleteMessageById(config.getMessageId()).queue(
                    success -> log.debug("Deleted old hub message in same channel"),
                    error -> log.warn("Old message already gone or missing permissions.")
            );

            event.deferReply(true).queue();
            channel.sendMessage("🛡️ **Re-initializing Clan Timer Hub...**").queue(msg -> {
                config.setMessageId(msg.getId());
                hubConfigRepository.save(config);
                event.getHook().sendMessage("Hub successfully refreshed in this channel!").queue();
            });
            return;
        }

        List<HubConfigEntity> otherHubs = hubConfigRepository.findByGuildId(guildId);

        // Scenario C: Hub exists in a DIFFERENT channel
        if (!otherHubs.isEmpty()) {
            event.reply("A Timer Hub already exists in another channel in this server. What would you like to do?")
                    .addActionRow(
                            Button.primary("hub_move", "Move Here"),
                            Button.secondary("hub_keep", "Keep Both"),
                            Button.danger("hub_cancel", "Cancel")
                    )
                    .setEphemeral(true).queue();
            return;
        }

        // Scenario A: First time setup in this server
        event.deferReply(true).queue();
        channel.sendMessage("🛡️ **Initializing Clan Timer Hub...**").queue(msg -> {
            HubConfigEntity config = new HubConfigEntity();
            config.setGuildId(guildId);
            config.setChannelId(channelId);
            config.setMessageId(msg.getId());
            hubConfigRepository.save(config);
            event.getHook().sendMessage("Hub successfully initialized!").queue();
        });
    }

    private void handleMoveHub(ButtonInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel)) {
            return;
        }
        TextChannel currentChannel = (TextChannel) event.getChannel();
        String guildId = event.getGuild().getId();

        List<HubConfigEntity> existingHubs = hubConfigRepository.findByGuildId(guildId);

        // Cleanup old messages
        for (HubConfigEntity hub : existingHubs) {
            TextChannel oldChannel = jda.getTextChannelById(hub.getChannelId());
            if (oldChannel != null) {
                oldChannel.deleteMessageById(hub.getMessageId()).queue(
                        success -> log.debug("Cleaned up old hub message"),
                        error -> {
                            log.warn("Failed to delete old hub message: {}", error.getMessage());
                            if (error.getMessage() != null && error.getMessage().contains("Missing Permissions")) {
                                event.getHook().sendMessage("⚠️ *Warning: I didn't have permission to delete the old hub message in <#" + hub.getChannelId() + ">.*").setEphemeral(true).queue();
                            }
                        }
                );
            }
        }

        // Remove old configs from DB
        hubConfigRepository.deleteAll(existingHubs);

        // Create new one here
        currentChannel.sendMessage("🛡️ **Initializing Clan Timer Hub...**").queue(msg -> {
            HubConfigEntity config = new HubConfigEntity();
            config.setGuildId(guildId);
            config.setChannelId(currentChannel.getId());
            config.setMessageId(msg.getId());
            hubConfigRepository.save(config);
            event.editMessage("Hub successfully moved to this channel!").setComponents().queue();
        });
    }

    private void handleKeepHub(ButtonInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel)) {
            return;
        }
        TextChannel channel = (TextChannel) event.getChannel();

        channel.sendMessage("🛡️ **Initializing Additional Clan Timer Hub...**").queue(msg -> {
            HubConfigEntity config = new HubConfigEntity();
            config.setGuildId(event.getGuild().getId());
            config.setChannelId(channel.getId());
            config.setMessageId(msg.getId());
            hubConfigRepository.save(config);
            event.editMessage("Second Hub successfully created! Both will now update automatically.").setComponents().queue();
        });
    }

    // ... (rest of your siege score logic remains unchanged)
    private void handleSiegeScoreCommand(SlashCommandInteractionEvent event) {
        OptionMapping tierOption = event.getOption("tier");
        Member member = event.getMember();

        if (member == null) {
            event.reply("Could not identify member.").setEphemeral(true).queue();
            return;
        }

        if (tierOption != null) {
            String tier = tierOption.getAsString();
            if ("T1".equalsIgnoreCase(tier)) {
                event.reply(formatSiegeScore(ClanGroup.T1)).queue();
            } else if ("T2".equalsIgnoreCase(tier)) {
                event.reply(formatSiegeScore(ClanGroup.T2)).queue();
            }
            return;
        }

        boolean hasT1 = hasRole(member, "T1") || hasRole(member, "Leadership");
        boolean hasT2 = hasRole(member, "T2") || hasRole(member, "Leadership");

        if (hasRole(member, "Leadership")) {
            hasT1 = true;
            hasT2 = true;
        }

        if (hasT1 && hasT2) {
            event.reply("You have access to both T1 and T2. Which score would you like?")
                    .addActionRow(
                            Button.primary("siege_score_t1", "T1 Score"),
                            Button.primary("siege_score_t2", "T2 Score")
                    )
                    .setEphemeral(true)
                    .queue();
        } else if (hasT1) {
            event.reply(formatSiegeScore(ClanGroup.T1)).queue();
        } else if (hasT2) {
            event.reply(formatSiegeScore(ClanGroup.T2)).queue();
        } else {
            event.reply("You do not have a T1 or T2 role assigned.").setEphemeral(true).queue();
        }
    }

    private boolean hasRole(Member member, String roleName) {
        List<Role> roles = member.getRoles();
        return roles.stream().anyMatch(r -> r.getName().contains(roleName));
    }

    private String formatSiegeScore(ClanGroup group) {
        Optional<SiegeEntity> siegeOpt = siegeService.getLatestBattleOrFinishedSiege(group);

        if (siegeOpt.isEmpty()) {
            return String.format("**[%s] No active Battle or Finished Siege found.**", group.getName());
        }

        SiegeEntity siege = siegeOpt.get();
        int myScore = siege.getTotalPoints();
        int oppScore = siege.getOpponentTotalPoints();
        String winIndicator = myScore > oppScore ? "🏆" : (myScore < oppScore ? "💀" : "🤝");

        return String.format(
                "**[%s] Siege Status: %s**\n" +
                        "🆚 **%s** vs **%s**\n" +
                        "📊 Score: **%d** - **%d** %s\n" +
                        "📅 Started: %s",
                group.getName(),
                siege.getStatus().getDisplayName(),
                group.getName(),
                siege.getOpponentClanName(),
                myScore,
                oppScore,
                winIndicator,
                siege.getStartDate().toLocalDate().toString()
        );
    }

    @PreDestroy
    public void stopBot() {
        if (jda != null) {
            jda.shutdown();
            log.info("Discord Bot stopped.");
        }
    }
}