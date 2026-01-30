package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
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

    public DiscordBotService(SiegeService siegeService) {
        this.siegeService = siegeService;
    }

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

            jda.awaitReady();
            log.info("Discord Bot is ONLINE as: {}", jda.getSelfUser().getAsTag());

            jda.updateCommands().addCommands(
                    Commands.slash("hello", "I will help with anything you want!"),
                    Commands.slash("robot", "What am I?")/*,
                    Commands.slash("website", "We have a clan website!"),
                    Commands.slash("siege-score", "Get the current Siege Score (Battle/Finished)")
                            .addOptions(new OptionData(OptionType.STRING, "tier", "Select T1 or T2 manually")
                                    .addChoice("T1", "T1")
                                    .addChoice("T2", "T2"))
                            .setDefaultPermissions(DefaultMemberPermissions.ENABLED)*/
            ).queue();

        } catch (Exception e) {
            log.error("Failed to start Discord Bot", e);
        }
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
        }
         else if (commandName.equals("website")) {
            event.reply("Clanwebsite! https://fotf-raid.com/")
                    .setEphemeral(false)
                    .queue();
        }
        else if (commandName.equals("siege-score")) {
            handleSiegeScoreCommand(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // CHANGED: setEphemeral(false) so the score result is visible to everyone
        if (event.getComponentId().equals("siege_score_t1")) {
            event.reply(formatSiegeScore(ClanGroup.T1)).setEphemeral(false).queue();
        } else if (event.getComponentId().equals("siege_score_t2")) {
            event.reply(formatSiegeScore(ClanGroup.T2)).setEphemeral(false).queue();
        }
    }

    private void handleSiegeScoreCommand(SlashCommandInteractionEvent event) {
        OptionMapping tierOption = event.getOption("tier");
        Member member = event.getMember();

        if (member == null) {
            event.reply("Could not identify member.").setEphemeral(true).queue();
            return;
        }

        // 1. Explicit Selection
        if (tierOption != null) {
            String tier = tierOption.getAsString();
            if ("T1".equalsIgnoreCase(tier)) {
                event.reply(formatSiegeScore(ClanGroup.T1)).queue();
            } else if ("T2".equalsIgnoreCase(tier)) {
                event.reply(formatSiegeScore(ClanGroup.T2)).queue();
            }
            return;
        }

        // 2. Role-Based Logic
        boolean hasT1 = hasRole(member, "T1") || hasRole(member, "Leadership");
        boolean hasT2 = hasRole(member, "T2") || hasRole(member, "Leadership");

        // Specific fix: If Leadership has NO specific clan tag, assume both.
        if (hasRole(member, "Leadership")) {
            hasT1 = true;
            hasT2 = true;
        }

        if (hasT1 && hasT2) {
            // The question/buttons remain Ephemeral (Private)
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