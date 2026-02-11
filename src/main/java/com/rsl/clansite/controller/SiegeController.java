package com.rsl.clansite.controller;

import com.rsl.clansite.model.OwnedChampion;
import com.rsl.clansite.model.SiegeStructure;
import com.rsl.clansite.model.dto.SiegeSlotAssignmentDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.MemberStatus;
import com.rsl.clansite.model.enums.SiegeStatus;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.repository.SiegeRepository;
import com.rsl.clansite.security.SecurityService;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiegeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/siege")
public class SiegeController {

    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final SiegeService siegeService;
    private final SecurityService securityService;
    private final SiegeRepository siegeRepository;
    private final ClanmemberRepository clanmemberRepository;
    private final ChampionRepository championRepository;

    public SiegeController(CommonsService commonsService,
                           ClanmemberService clanmemberService,
                           SiegeService siegeService,
                           SiegeRepository siegeRepository,
                           ClanmemberRepository clanmemberRepository,
                           ChampionRepository championRepository,
                           SecurityService securityService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.siegeService = siegeService;
        this.siegeRepository = siegeRepository;
        this.clanmemberRepository = clanmemberRepository;
        this.championRepository = championRepository;
        this.securityService = securityService;
    }

    // --- GET MAPPINGS ---

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public String siegeLanding(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);
        return "siege-landing";
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('MEMBER')")
    public String siegeOverview(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);
        setupSiegeModel(model, session, authentication);
        return "siege-overview";
    }

    @GetMapping("/defense")
    @PreAuthorize("hasRole('MEMBER')")
    public String siegeDefenseMap(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);
        setupSiegeModel(model, session, authentication);

        // --- NEW: Inject Active Conditions for Dropdowns ---
        List<SiegeConditionEntity> activeConditions = siegeService.getActiveConditions();
        model.addAttribute("activeConditions", activeConditions);
        // ---------------------------------------------------

        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        String discordId = activeMember.getDiscordId();

        boolean isPrivileged = (boolean) model.getAttribute("isPrivileged");

        // --- Data Loading Strategy ---
        List<ClanmemberEntity> profilesForData;
        Map<ClanGroup, ClanmemberEntity> myProfiles = new HashMap<>();

        if (isPrivileged) {
            profilesForData = clanmemberRepository.findAll();
            List<ClanmemberEntity> t1Members = profilesForData.stream()
                    .filter(m -> m.getClanGroup() == ClanGroup.T1)
                    .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                    .collect(Collectors.toList());

            List<ClanmemberEntity> t2Members = profilesForData.stream()
                    .filter(m -> m.getClanGroup() == ClanGroup.T2)
                    .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                    .collect(Collectors.toList());

            Comparator<ClanmemberEntity> nameSorter = Comparator.comparing(ClanmemberEntity::getIngameName, String.CASE_INSENSITIVE_ORDER);
            t1Members.sort(nameSorter);
            t2Members.sort(nameSorter);

            model.addAttribute("t1MembersJs", toJsList(t1Members));
            model.addAttribute("t2MembersJs", toJsList(t2Members));

            List<ClanmemberEntity> myOwn = clanmemberRepository.findAllByDiscordId(discordId);
            myOwn.forEach(p -> myProfiles.put(p.getClanGroup(), p));
        } else {
            profilesForData = clanmemberRepository.findAllByDiscordId(discordId);
            profilesForData.forEach(p -> myProfiles.put(p.getClanGroup(), p));
        }

        model.addAttribute("profiles", myProfiles);

        List<SiegeEntity> siegeList = (List<SiegeEntity>) model.getAttribute("siegeList");
        Map<String, Long> usedSlotsMap = new HashMap<>();

        if (siegeList != null) {
            for (SiegeEntity siege : siegeList) {
                ClanmemberEntity profile = myProfiles.get(siege.getClanGroup());
                if (profile != null) {
                    long count = siegeService.countUsedSlots(siege, profile.getId().toHexString());
                    usedSlotsMap.put(siege.getId().toHexString(), count);
                } else {
                    usedSlotsMap.put(siege.getId().toHexString(), 0L);
                }
            }
        }
        model.addAttribute("usedSlotsMap", usedSlotsMap);

        // --- UPDATED: Map Instance UUIDs to Champion Names ---
        Map<String, String> championNames = new HashMap<>();
        Set<String> masterIdsToCheck = new HashSet<>();
        Map<String, String> instanceToMasterIdMap = new HashMap<>();

        for (ClanmemberEntity p : profilesForData) {
            if (p.getRoster() != null) {
                for (OwnedChampion oc : p.getRoster()) {
                    masterIdsToCheck.add(oc.getChampionId());
                    instanceToMasterIdMap.put(oc.getId(), oc.getChampionId());
                }
            }
        }

        if (!masterIdsToCheck.isEmpty()) {
            List<ObjectId> objectIds = masterIdsToCheck.stream()
                    .filter(ObjectId::isValid)
                    .map(ObjectId::new)
                    .collect(Collectors.toList());

            if (!objectIds.isEmpty()) {
                Map<String, String> masterIdToName = championRepository.findAllById(objectIds).stream()
                        .collect(Collectors.toMap(c -> c.getId().toHexString(), ChampionEntity::getName));

                for (Map.Entry<String, String> entry : instanceToMasterIdMap.entrySet()) {
                    String instanceId = entry.getKey();
                    String masterId = entry.getValue();
                    if (masterIdToName.containsKey(masterId)) {
                        String name = masterIdToName.get(masterId);
                        championNames.put(instanceId, name);
                    }
                }
            }
        }
        model.addAttribute("championNames", championNames);
        // ---------------------------------------------------

        return "siege-defense";
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('MEMBER')")
    public String siegeHistory(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        List<SiegeEntity> t1History = siegeRepository.findByClanGroupAndStatusOrderByStartDateDesc(ClanGroup.T1, SiegeStatus.FINISHED);
        List<SiegeEntity> t2History = siegeRepository.findByClanGroupAndStatusOrderByStartDateDesc(ClanGroup.T2, SiegeStatus.FINISHED);

        model.addAttribute("t1History", t1History);
        model.addAttribute("t2History", t2History);
        return "siege-history";
    }

    @GetMapping("/history/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public String viewHistoryDetails(@PathVariable String id, Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        SiegeEntity siege = siegeRepository.findById(new ObjectId(id))
                .orElseThrow(() -> new IllegalArgumentException("Siege not found"));

        model.addAttribute("siegeList", Collections.singletonList(siege));
        model.addAttribute("isHistoryView", true);
        model.addAttribute("primaryGroup", siege.getClanGroup());

        model.addAttribute("isPrivileged", securityService.isCoordinator(authentication));

        return "siege-overview";
    }

    // --- ACTIONS ---

    @PostMapping("/assign-slot")
    @PreAuthorize("hasRole('MEMBER')")
    public String assignDefenseSlot(@ModelAttribute SiegeSlotAssignmentDTO assignmentDTO,
                                    RedirectAttributes redirectAttributes,
                                    Authentication authentication,
                                    HttpSession session) {

        if (!hasPermission(session, authentication, assignmentDTO.getMemberId())) {
            redirectAttributes.addFlashAttribute("error", "You do not have permission to edit this slot.");
            return "redirect:/siege/defense";
        }

        try {
            siegeService.assignDefenseTeam(
                    assignmentDTO.getSiegeId(),
                    assignmentDTO.getStructureId(),
                    assignmentDTO.getSlotNumber(),
                    assignmentDTO.getMemberId(),
                    assignmentDTO.getLeaderChampionId(),
                    assignmentDTO.getSupportChampionIds(),
                    authentication
            );
            redirectAttributes.addFlashAttribute("success", "Defense team assigned successfully!");
        } catch (Exception e) {
            log.error("Failed to assign defense slot", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/siege/defense";
    }

    @PostMapping("/clear-slot")
    @PreAuthorize("hasRole('MEMBER')")
    public String clearDefenseSlot(@RequestParam String siegeId,
                                   @RequestParam String structureId,
                                   @RequestParam int slotNumber,
                                   @RequestParam String memberId,
                                   RedirectAttributes redirectAttributes,
                                   Authentication authentication,
                                   HttpSession session) {

        if (!hasPermission(session, authentication, memberId)) {
            redirectAttributes.addFlashAttribute("error", "You do not have permission to clear this slot.");
            return "redirect:/siege/defense";
        }

        try {
            siegeService.assignDefenseTeam(siegeId, structureId, slotNumber, null, null, new ArrayList<>(), authentication);
            redirectAttributes.addFlashAttribute("success", "Defense slot cleared successfully!");
        } catch (Exception e) {
            log.error("Failed to clear defense slot", e);
            redirectAttributes.addFlashAttribute("error", "Failed to clear slot: " + e.getMessage());
        }

        return "redirect:/siege/defense";
    }

    @PostMapping("/structure/update")
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN') or hasRole('OWNER')")
    public Object updateStructureStatus(@RequestParam("siegeId") String siegeId,
                                        @RequestParam("structureId") String structureId,
                                        @RequestParam("mapType") String mapType,
                                        @RequestParam("isCleared") boolean isCleared,
                                        HttpServletRequest request) {

        Optional<SiegeEntity> siegeOpt = siegeRepository.findById(new ObjectId(siegeId));
        if (siegeOpt.isEmpty()) {
            return "redirect:/siege/overview";
        }

        SiegeEntity siege = siegeOpt.get();
        List<SiegeStructure> targetList = mapType.equals("DEFENSE") ? siege.getDefensiveStructures() : siege.getTargetStructures();

        for (SiegeStructure structure : targetList) {
            if (structure.getId().equals(structureId)) {
                structure.setCleared(isCleared);
                break;
            }
        }
        siegeRepository.save(siege);

        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("newDefensePoints", siege.getCurrentDefensePoints());
            response.put("newAttackPoints", siege.getCurrentAttackPoints());
            response.put("newTotalPoints", siege.getTotalPoints());
            response.put("oppDefensePoints", siege.getOpponentDefensePoints());
            response.put("oppAttackPoints", siege.getOpponentAttackPoints());
            response.put("oppTotalPoints", siege.getOpponentTotalPoints());

            return ResponseEntity.ok(response);
        }

        return "redirect:/siege/overview";
    }

    @PostMapping("/{siegeId}/structure/{structureId}/upgrade")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_COORDINATOR')")
    public ResponseEntity<SiegeStructure> upgradeStructure(
            @PathVariable String siegeId,
            @PathVariable String structureId) {

        SiegeStructure updatedStructure = siegeService.upgradeStructure(siegeId, structureId);

        return ResponseEntity.ok(updatedStructure);
    }

    // --- NEW ACTION: Update Structure Conditions ---
    @PostMapping("/structure/conditions")
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN') or hasRole('OWNER')")
    public String updateStructureConditions(@RequestParam String siegeId,
                                            @RequestParam String structureId,
                                            @RequestParam(required = false) List<String> conditionKeys,
                                            RedirectAttributes redirectAttributes,
                                            Authentication authentication) {
        try {
            siegeService.updateStructureConditions(siegeId, structureId, conditionKeys, authentication);
            redirectAttributes.addFlashAttribute("success", "Conditions updated successfully.");
        } catch (Exception e) {
            log.error("Failed to update structure conditions", e);
            redirectAttributes.addFlashAttribute("error", "Update failed: " + e.getMessage());
        }
        return "redirect:/siege/defense";
    }

    @GetMapping("/scrolls")
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN') or hasRole('OWNER')")
    public String siegeScrollsManagement(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        prepareScrollStats(model, ClanGroup.T1, "t1");
        prepareScrollStats(model, ClanGroup.T2, "t2");

        return "siege-scrolls";
    }

    @PostMapping("/scrolls/defense/update")
    @ResponseBody
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> updateDefenseScrolls(
            @RequestParam String memberId,
            @RequestParam int delta,
            Authentication authentication) {

        int newVal = clanmemberService.updateDefenseScrolls(memberId, delta, authentication);

        ClanmemberEntity member = clanmemberService.getMemberById(memberId);

        // Calculate the new total for the whole clan so the header updates instantly
        List<ClanmemberEntity> clanMembers = clanmemberRepository.findByClanGroupAndStatus(member.getClanGroup(), MemberStatus.ACTIVE);
        int newClanTotal = clanMembers.stream().mapToInt(m -> m.getMaxDefenseScrolls() > 0 ? m.getMaxDefenseScrolls() : 2).sum();

        Map<String, Object> response = new HashMap<>();
        response.put("memberId", memberId);
        response.put("newValue", newVal);
        response.put("clanTotal", newClanTotal);
        response.put("clanGroup", member.getClanGroup().name());

        return ResponseEntity.ok(response);
    }

    // --- HELPER METHODS ---

    private boolean hasPermission(HttpSession session, Authentication authentication, String targetMemberId) {
        ClanmemberEntity activeUser = clanmemberService.getActiveClanmember(session, authentication);
        List<ClanmemberEntity> userProfiles = clanmemberRepository.findAllByDiscordId(activeUser.getDiscordId());

        boolean isSelf = userProfiles.stream()
                .anyMatch(p -> p.getId().toHexString().equals(targetMemberId));

        return isSelf || securityService.isCoordinator(authentication);
    }

    private void setupSiegeModel(Model model, HttpSession session, Authentication authentication) {
        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        ClanGroup primaryGroup = resolveClanGroup(session, authentication);
        ClanGroup secondaryGroup = (primaryGroup == ClanGroup.T1) ? ClanGroup.T2 : ClanGroup.T1;

        SiegeEntity primarySiege = getOrCreateSiege(primaryGroup);
        SiegeEntity secondarySiege = getOrCreateSiege(secondaryGroup);

        List<SiegeEntity> siegeList = new ArrayList<>();
        siegeList.add(primarySiege);
        siegeList.add(secondarySiege);

        model.addAttribute("siegeList", siegeList);
        model.addAttribute("primaryGroup", primaryGroup);
        model.addAttribute("currentUser", activeMember);

        model.addAttribute("isPrivileged", securityService.isCoordinator(authentication));

        if (!model.containsAttribute("isHistoryView")) {
            model.addAttribute("isHistoryView", false);
        }
    }

    private ClanGroup resolveClanGroup(HttpSession session, Authentication authentication) {
        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        ClanGroup primaryGroup = activeMember.getClanGroup();
        if (primaryGroup == null) primaryGroup = ClanGroup.T1;

        Object switchedGroupObj = session.getAttribute("switchedClanGroup");
        if (switchedGroupObj != null) {
            try {
                primaryGroup = ClanGroup.valueOf(switchedGroupObj.toString());
            } catch (IllegalArgumentException e) { }
        }
        return primaryGroup;
    }

    private SiegeEntity getOrCreateSiege(ClanGroup group) {
        return siegeService.getActiveSiege(group)
                .orElseGet(() -> siegeService.createNextSiege(group, LocalDateTime.now()));
    }

    private List<Map<String, Object>> toJsList(List<ClanmemberEntity> members) {
        return members.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId().toHexString());
            map.put("ingameName", m.getIngameName());

            // Extract IDs from OwnedChampion list
            List<String> instanceIds = (m.getRoster() != null) ?
                    m.getRoster().stream().map(OwnedChampion::getId).collect(Collectors.toList()) :
                    Collections.emptyList();

            map.put("rosterChampionIds", instanceIds);
            map.put("knownTeams", m.getKnownTeams());
            return map;
        }).collect(Collectors.toList());
    }

    private void prepareScrollStats(Model model, ClanGroup group, String prefix) {
        // 1. Get Active Members
        List<ClanmemberEntity> members = clanmemberRepository.findByClanGroupAndStatus(group, MemberStatus.ACTIVE);
        members.sort(Comparator.comparing(ClanmemberEntity::getIngameName, String.CASE_INSENSITIVE_ORDER));

        // 2. Calculate Actual Capacity (Sum of Scrolls)
        int totalScrolls = members.stream()
                .mapToInt(m -> m.getMaxDefenseScrolls() > 0 ? m.getMaxDefenseScrolls() : 2)
                .sum();

        // 3. Calculate Target (Siege Slots)
        // Try active siege first, fallback to latest finished if between wars
        Optional<SiegeEntity> siegeOpt = siegeService.getActiveSiege(group);
        if (siegeOpt.isEmpty()) {
            siegeOpt = siegeService.getLatestBattleOrFinishedSiege(group);
        }

        int targetSlots = siegeOpt.map(siegeService::calculateTotalDefenseSlots).orElse(0);

        // 4. Add to Model
        model.addAttribute(prefix + "Members", members);
        model.addAttribute(prefix + "TotalScrolls", totalScrolls);
        model.addAttribute(prefix + "TargetSlots", targetSlots);
    }
}