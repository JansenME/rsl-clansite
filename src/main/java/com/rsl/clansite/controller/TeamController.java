package com.rsl.clansite.controller;

import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.OwnedChampion;
import com.rsl.clansite.model.Team;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.security.SecurityService;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiegeConditionService;
import jakarta.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.beans.PropertyEditorSupport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teams")
public class TeamController {

    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final ChampionRepository championRepository;
    private final ChampionsService championsService;
    private final SiegeConditionService siegeConditionService;
    private final SecurityService securityService;

    public TeamController(CommonsService commonsService,
                          ClanmemberService clanmemberService,
                          ChampionRepository championRepository,
                          ChampionsService championsService,
                          SiegeConditionService siegeConditionService,
                          SecurityService securityService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.championRepository = championRepository;
        this.championsService = championsService;
        this.siegeConditionService = siegeConditionService;
        this.securityService = securityService;
    }

    public record ConditionDropdownItem(String id, String label, String category, String value) {}

    public record TeamBuilderOptionDTO(
            String id,
            String label,
            String imageName,
            String rarity,
            String rarityKey, // Raw Enum Key
            String type,
            String typeKey,   // Raw Enum Key
            String faction,
            String factionKey,// Raw Enum Key
            String alliance,
            String allianceKey, // NEW: Raw Enum Key for Alliance
            String affinity,
            String affinityKey,// Raw Enum Key
            String auraLoc,
            String auraDesc,
            int level,
            int rank,
            Double sortScore
    ) {}

    @GetMapping("/builder")
    @PreAuthorize("hasRole('MEMBER')")
    public String builder(@RequestParam(required = false) String editTeamId,
                          @RequestParam(required = false) String targetMemberId,
                          Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        ClanmemberEntity activeMember;

        if (StringUtils.hasText(targetMemberId)) {
            ClanmemberEntity target = clanmemberService.getMemberById(targetMemberId);
            boolean isOwnProfile = clanmemberService.isOwnProfile(target, authentication);

            if (isOwnProfile || securityService.isCoordinator(authentication)) {
                activeMember = target;
            } else {
                return "redirect:/";
            }
        } else {
            activeMember = clanmemberService.getActiveClanmember(session, authentication);
        }

        if (activeMember == null) {
            return "redirect:/";
        }

        model.addAttribute("targetMemberId", activeMember.getId().toHexString());

        List<OwnedChampion> roster = activeMember.getRoster() != null ? activeMember.getRoster() : List.of();

        List<String> masterIds = roster.stream()
                .map(OwnedChampion::getChampionId)
                .collect(Collectors.toList());

        Map<String, Champion> masterMap = championsService.getChampionsByIds(masterIds).stream()
                .collect(Collectors.toMap(Champion::getId, c -> c));

        List<TeamBuilderOptionDTO> championOptions = new ArrayList<>();

        for (OwnedChampion instance : roster) {
            Champion master = masterMap.get(instance.getChampionId());
            if (master != null) {
                String label = master.getName();

                String auraLoc = "NONE";
                String auraDesc = "No Aura";
                if (master.getAura() != null) {
                    auraLoc = master.getAura().getLocation().name();
                    auraDesc = master.getAura().getStat().getName() + " " +
                            master.getAura().getAmount() +
                            (master.getAura().isPercentage() ? "%" : "") +
                            " in " + master.getAura().getLocation().getName();
                }

                // Display Names (Localized/Capitalized)
                String rarityName = resolveEnumName(master.getRarity());
                String typeName = resolveEnumName(master.getType());
                String factionName = resolveEnumName(master.getFaction());
                String affinityName = resolveEnumName(master.getAffinity());

                // Raw Keys (For Logic Matching)
                String rarityKey = master.getRarity() != null ? master.getRarity().name() : "";
                String typeKey = master.getType() != null ? master.getType().name() : "";
                String factionKey = master.getFaction() != null ? master.getFaction().name() : "";
                String affinityKey = master.getAffinity() != null ? master.getAffinity().name() : "";

                // Resolve Alliance & Key
                String allianceName = "";
                String allianceKey = "";
                if (master.getFaction() != null && master.getFaction().getAlliance() != null) {
                    allianceName = resolveEnumName(master.getFaction().getAlliance());
                    allianceKey = master.getFaction().getAlliance().name();
                }

                // Calculate Shadow Score for Sorting
                double sortScore = 0.0;
                if (master.getRarity() != null) {
                    sortScore += master.getRarity().ordinal() * 10000;
                }
                sortScore += instance.getRank() * 100;
                sortScore += instance.getLevel();

                championOptions.add(new TeamBuilderOptionDTO(
                        instance.getId(),
                        master.getName(),
                        master.getImagename(),
                        rarityName,
                        rarityKey,
                        typeName,
                        typeKey,
                        factionName,
                        factionKey,
                        allianceName,
                        allianceKey, // Pass Key
                        affinityName,
                        affinityKey,
                        auraLoc,
                        auraDesc,
                        instance.getLevel(),
                        instance.getRank(),
                        sortScore
                ));
            }
        }

        championOptions.sort((a, b) -> {
            int nameCompare = a.label().compareToIgnoreCase(b.label());
            if (nameCompare != 0) return nameCompare;
            return Integer.compare(b.level(), a.level()); // Same name? High level first
        });

        model.addAttribute("championOptions", championOptions);

        List<SiegeConditionEntity> activeEntities = siegeConditionService.findAllConditions().stream()
                .filter(SiegeConditionEntity::isActive)
                .collect(Collectors.toList());

        activeEntities.sort((a, b) -> {
            // Sort by Category Priority (Rarity -> Type -> Affinity -> Faction -> Alliance)
            if (a.getCategory() != b.getCategory()) {
                return Integer.compare(a.getCategory().ordinal(), b.getCategory().ordinal());
            }

            // Sort by Value within Category (e.g., Legendary vs Epic)
            try {
                // Fix: Use raw 'Class' type to bypass generic capture error
                Class enumClass = a.getCategory().getEnumClass();

                // Suppress the "unchecked" warning since we know it's safe here
                @SuppressWarnings("unchecked")
                Enum<?> enumA = Enum.valueOf(enumClass, a.getConditionKey());

                @SuppressWarnings("unchecked")
                Enum<?> enumB = Enum.valueOf(enumClass, b.getConditionKey());

                return Integer.compare(enumA.ordinal(), enumB.ordinal());
            } catch (Exception e) {
                // Fallback to alphabetical if Enum lookup fails
                return a.getConditionKey().compareTo(b.getConditionKey());
            }
        });

        List<ConditionDropdownItem> dropdownItems = new ArrayList<>();

        for (SiegeConditionEntity entity : activeEntities) {
            String readableName = resolveEnumName(entity);
            String label = entity.getCategory().getDisplayName() + ": " + readableName;

            dropdownItems.add(new ConditionDropdownItem(
                    entity.getId().toHexString(),
                    label,
                    entity.getCategory().name(),
                    entity.getConditionKey()
            ));
        }

        model.addAttribute("siegeConditions", dropdownItems);

        Team formTeam = new Team();
        if (editTeamId != null && activeMember.getKnownTeams() != null) {
            formTeam = activeMember.getKnownTeams().stream()
                    .filter(t -> t.getId().equals(editTeamId))
                    .findFirst()
                    .orElse(new Team());
        }

        model.addAttribute("newTeam", formTeam);

        return "team-builder";
    }

    // ... (Rest of Controller Unchanged) ...
    @PostMapping("/save")
    @PreAuthorize("hasRole('MEMBER')")
    public String saveTeam(@ModelAttribute Team team,
                           @RequestParam(required = false) String targetMemberId,
                           Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            clanmemberService.saveKnownTeam(session, authentication, team, targetMemberId);
            redirectAttributes.addFlashAttribute("successMessage", "Team saved successfully!");

            if (StringUtils.hasText(targetMemberId)) {
                return "redirect:/profile/" + targetMemberId;
            }
            return "redirect:/profile";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving team: " + e.getMessage());

            String redirectUrl = "redirect:/teams/builder";
            if (StringUtils.hasText(targetMemberId)) {
                redirectUrl += "?targetMemberId=" + targetMemberId;
            }
            return redirectUrl;
        }
    }

    @PostMapping("/delete/{teamId}")
    @PreAuthorize("hasRole('MEMBER')")
    public String deleteTeam(@PathVariable String teamId,
                             @RequestParam(required = false) String targetMemberId,
                             Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            clanmemberService.deleteKnownTeam(session, authentication, teamId, targetMemberId);
            redirectAttributes.addFlashAttribute("successMessage", "Team deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting team: " + e.getMessage());
        }

        if (StringUtils.hasText(targetMemberId)) {
            return "redirect:/profile/" + targetMemberId;
        }
        return "redirect:/profile";
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(ObjectId.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) throws IllegalArgumentException {
                if (StringUtils.hasText(text)) {
                    setValue(new ObjectId(text));
                } else {
                    setValue(null);
                }
            }
        });
    }

    private String resolveEnumName(SiegeConditionEntity entity) {
        try {
            Class<? extends Enum<?>> enumClass = entity.getCategory().getEnumClass();
            for (Enum<?> constant : enumClass.getEnumConstants()) {
                if (constant.name().equals(entity.getConditionKey())) {
                    Method getNameMethod = constant.getClass().getMethod("getName");
                    return (String) getNameMethod.invoke(constant);
                }
            }
        } catch (Exception e) {
            return entity.getConditionKey();
        }
        return entity.getConditionKey();
    }

    private String resolveEnumName(Enum<?> enumVal) {
        if (enumVal == null) return "";
        try {
            Method m = enumVal.getClass().getMethod("getName");
            return (String) m.invoke(enumVal);
        } catch (Exception e) {
            String name = enumVal.name().toLowerCase().replace("_", " ");
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }
}