package com.rsl.clansite.controller;

import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.CompleteChampionsFilter;
import com.rsl.clansite.model.OwnedChampion;
import com.rsl.clansite.model.Team;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.security.SecurityService;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiegeConditionService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/profile")
public class ProfileController {
    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final ChampionsService championsService;
    private final SiegeConditionService siegeConditionService;
    private final SecurityService securityService;

    public ProfileController(CommonsService commonsService,
                             ClanmemberService clanmemberService,
                             ChampionsService championsService,
                             SiegeConditionService siegeConditionService,
                             SecurityService securityService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.championsService = championsService;
        this.siegeConditionService = siegeConditionService;
        this.securityService = securityService;
    }

    public record TeamViewDTO(
            String id,
            String teamName,
            String conditionLabel,
            Champion leader,
            Champion member2,
            Champion member3,
            Champion member4
    ) {}

    // DTO to combine Master Data (Image/Name) with Instance Data (Level/Rank)
    public record RosterEntryDTO(
            Champion masterData,
            OwnedChampion instanceData
    ) {}

    @GetMapping(value={"", "/"})
    @PreAuthorize("isAuthenticated()")
    public String profileRedirect(Authentication authentication, HttpSession session, Model model) {
        String activeMemberId = clanmemberService.manageActiveMemberSession(session, authentication);

        if (activeMemberId != null) {
            return "redirect:/profile/" + activeMemberId;
        }

        commonsService.fillModel(model, authentication, session);
        model.addAttribute("isOwnProfile", true);

        model.addAttribute("rosterEntries", List.of());
        addFilterDataToModel(model);

        return "profile";
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String viewMemberProfile(@PathVariable String id,
                                    Model model,
                                    Authentication authentication,
                                    HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        log.info("Member ID provided is ID: {}", id);
        ClanmemberEntity targetMember = clanmemberService.getMemberById(id);

        String currentDiscordId = getDiscordIdFromAuthentication(authentication);

        List<ClanmemberEntity> myAccounts = clanmemberService.getLinkedClanmembers(currentDiscordId);

        boolean isOwnProfile = myAccounts.stream()
                .anyMatch(account -> account.getId().toHexString().equals(id));

        boolean isMember = securityService.hasRole(authentication, "ROLE_MEMBER");

        if (!isOwnProfile && !isMember) {
            throw new AccessDeniedException("You do not have permission to view other member profiles.");
        }

        if (isOwnProfile) {
            model.addAttribute("linkedMembers", myAccounts);
        }

        // --- New Roster Logic ---
        List<OwnedChampion> ownedRoster = targetMember.getRoster() != null ? targetMember.getRoster() : List.of();

        // 1. Collect all unique Master IDs
        Set<String> masterIds = ownedRoster.stream()
                .map(OwnedChampion::getChampionId)
                .collect(Collectors.toSet());

        // 2. Fetch Master Data
        Map<String, Champion> masterMap = championsService.getChampionsByIds(new ArrayList<>(masterIds)).stream()
                .collect(Collectors.toMap(Champion::getId, c -> c));

        // 3. Build Composite DTOs with Updated Sorting
        List<RosterEntryDTO> rosterEntries = ownedRoster.stream()
                .filter(oc -> masterMap.containsKey(oc.getChampionId()))
                .map(oc -> new RosterEntryDTO(masterMap.get(oc.getChampionId()), oc))
                .sorted((a, b) -> {
                    // 1. Rarity DESC (Mythical -> Common)
                    // Enums compare based on ordinal definition. Descending means b.compareTo(a).
                    int rarityCompare = b.masterData().getRarity().compareTo(a.masterData().getRarity());
                    if (rarityCompare != 0) return rarityCompare;

                    // 2. Rank DESC (6 -> 1)
                    int rankCompare = Integer.compare(b.instanceData().getRank(), a.instanceData().getRank());
                    if (rankCompare != 0) return rankCompare;

                    // 3. Level DESC (60 -> 1)
                    int levelCompare = Integer.compare(b.instanceData().getLevel(), a.instanceData().getLevel());
                    if (levelCompare != 0) return levelCompare;

                    // 4. Name ASC (A -> Z)
                    return a.masterData().getName().compareTo(b.masterData().getName());
                })
                .collect(Collectors.toList());

        model.addAttribute("rosterEntries", rosterEntries);
        // ------------------------

        addFilterDataToModel(model);

        ClanmemberViewData targetViewData = clanmemberService.getViewDataForMember(targetMember);
        model.addAttribute("clanmemberViewData", targetViewData);
        model.addAttribute("member", targetMember);
        model.addAttribute("isOwnProfile", isOwnProfile);

        List<TeamViewDTO> knownTeamsView = buildKnownTeamsView(targetMember.getKnownTeams(), targetMember);
        model.addAttribute("knownTeams", knownTeamsView);

        model.addAttribute("partOfDiscord", false);

        if (StringUtils.hasText(targetMember.getDiscordId())) {
            model.addAttribute("partOfDiscord", true);
        }

        return "profile";
    }

    @PostMapping("/switch")
    @PreAuthorize("isAuthenticated()")
    public String switchAccount(
            @RequestParam("memberId") String newActiveMemberId,
            Authentication authentication,
            HttpSession session) {

        clanmemberService.switchActiveMember(session, authentication, newActiveMemberId);

        return "redirect:/profile";
    }

    private List<TeamViewDTO> buildKnownTeamsView(List<Team> rawTeams, ClanmemberEntity member) {
        if (rawTeams == null || rawTeams.isEmpty()) {
            return List.of();
        }

        // Map Instance UUIDs to OwnedChampions for O(1) lookup
        Map<String, OwnedChampion> rosterMap = (member.getRoster() != null) ?
                member.getRoster().stream().collect(Collectors.toMap(OwnedChampion::getId, c -> c)) :
                new HashMap<>();

        Set<String> masterChampionIdsToCheck = new HashSet<>();

        // Resolve Team UUIDs to Master IDs
        for (Team t : rawTeams) {
            resolveAndAddMasterId(t.getLeaderChampionId(), rosterMap, masterChampionIdsToCheck);
            resolveAndAddMasterId(t.getChampion2Id(), rosterMap, masterChampionIdsToCheck);
            resolveAndAddMasterId(t.getChampion3Id(), rosterMap, masterChampionIdsToCheck);
            resolveAndAddMasterId(t.getChampion4Id(), rosterMap, masterChampionIdsToCheck);
        }

        Map<String, Champion> championMap = championsService.getChampionsByIds(new ArrayList<>(masterChampionIdsToCheck))
                .stream()
                .collect(Collectors.toMap(Champion::getId, c -> c));

        Map<ObjectId, SiegeConditionEntity> conditionMap = siegeConditionService.findAllConditions()
                .stream()
                .collect(Collectors.toMap(SiegeConditionEntity::getId, c -> c));

        List<TeamViewDTO> viewList = new ArrayList<>();

        for (Team t : rawTeams) {
            String conditionLabel = null;
            if (t.getSiegeConditionId() != null) {
                SiegeConditionEntity cond = conditionMap.get(t.getSiegeConditionId());
                if (cond != null) {
                    conditionLabel = cond.getCategory().getDisplayName() + ": " + cond.getConditionKey();
                }
            }

            viewList.add(new TeamViewDTO(
                    t.getId(),
                    t.getTeamName(),
                    conditionLabel,
                    resolveMasterChampion(t.getLeaderChampionId(), rosterMap, championMap),
                    resolveMasterChampion(t.getChampion2Id(), rosterMap, championMap),
                    resolveMasterChampion(t.getChampion3Id(), rosterMap, championMap),
                    resolveMasterChampion(t.getChampion4Id(), rosterMap, championMap)
            ));
        }

        return viewList;
    }

    private void resolveAndAddMasterId(String instanceId, Map<String, OwnedChampion> rosterMap, Set<String> masterIds) {
        if (instanceId != null && rosterMap.containsKey(instanceId)) {
            masterIds.add(rosterMap.get(instanceId).getChampionId());
        }
    }

    private Champion resolveMasterChampion(String instanceId, Map<String, OwnedChampion> rosterMap, Map<String, Champion> masterMap) {
        if (instanceId != null && rosterMap.containsKey(instanceId)) {
            String masterId = rosterMap.get(instanceId).getChampionId();
            return masterMap.get(masterId);
        }
        return null; // Handle case where champion was deleted from roster but remains in team
    }

    private void addFilterDataToModel(Model model) {
        model.addAttribute("filtersWrapper", new CompleteChampionsFilter());
        model.addAttribute("rarities", Rarity.values());
        model.addAttribute("types", Type.values());
        model.addAttribute("affinities", Affinity.values());
        model.addAttribute("factions", Faction.values());
        model.addAttribute("alliances", Alliance.values());
    }

    private String getDiscordIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            throw new AccessDeniedException("Invalid authentication state");
        }

        String discordId = oauthUser.getAttribute("id");
        if (discordId == null) {
            throw new AccessDeniedException("Could not retrieve Discord ID from session");
        }
        return discordId;
    }
}