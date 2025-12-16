package com.rsl.clansite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.exceptions.UnlinkedAccountException;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
@Slf4j
public class ClanmemberService {
    private static final String DISCORD_MEMBER_API_BASE = "https://discord.com/api/v10/guilds/";

    private final ClanmemberRepository clanmemberRepository;
    private final DiscordRoleService discordRoleService;

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${discord.bot-token}")
    private String botToken;

    @Value("${discord.clan-server-id}")
    private String clanServerId;

    @Autowired
    public ClanmemberService(ClanmemberRepository clanmemberRepository, final DiscordRoleService discordRoleService) {
        this.clanmemberRepository = clanmemberRepository;
        this.discordRoleService = discordRoleService;
    }

    public void linkClanmember(final String discordId, final String globalName, final String avatarHash, final List<String> currentDiscordRoles) {
        List<ClanmemberEntity> linkedMembers = clanmemberRepository.findAllByDiscordId(discordId);

        if (linkedMembers.isEmpty()) {
            log.warn("Link attempt for user {} failed. No roster entry found.", discordId);
            return;
        }

        final List<String> masterOrder = discordRoleService.getOrderedRoleIds();
        List<String> sortedRoles = new java.util.ArrayList<>(currentDiscordRoles);

        sortedRoles.sort((id1, id2) -> {
            int index1 = masterOrder.indexOf(id1);
            int index2 = masterOrder.indexOf(id2);

            if (index1 == -1 && index2 == -1) return 0;
            if (index1 == -1) return 1;
            if (index2 == -1) return -1;

            return Integer.compare(index1, index2);
        });

        for (ClanmemberEntity member : linkedMembers) {
            member.setDiscordName(globalName);
            member.setAvatarHash(avatarHash);
            member.setDiscordRoles(sortedRoles);
            clanmemberRepository.save(member);
        }
    }

    public List<ClanmemberEntity> getLinkedClanmembers(final String discordId) {
        List<ClanmemberEntity> linkedMembers = clanmemberRepository.findAllByDiscordId(discordId);

        if (linkedMembers.isEmpty()) {
            throw new UnlinkedAccountException("User's Discord ID is not linked. Please contact the administrator.");
        }

        return linkedMembers;
    }

    public List<ClanmemberEntity> findAllClanmemberEntities() {
        return clanmemberRepository.findAll();
    }

    public ClanmemberViewData getUserViewData(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            return new ClanmemberViewData(null, List.of(), null);
        }

        String discordId = oauth2User.getAttribute("id");
        String globalName = oauth2User.getAttribute("global_name");
        String discordUserName = (globalName != null) ? globalName : "Unknown User";

        String avatarHash = oauth2User.getAttribute("avatar");

        String discordAvatarUrl = (discordId != null && avatarHash != null)
                ? "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".png"
                : null;

        List<String> roleNames = List.of("No Discord Roles Found");

        List<ClanmemberEntity> linkedMembers = clanmemberRepository.findAllByDiscordId(discordId);

        if (!linkedMembers.isEmpty()) {
            List<String> discordRoleIds = linkedMembers.get(0).getDiscordRoles();

            if (discordRoleIds != null && !discordRoleIds.isEmpty()) {
                roleNames = discordRoleIds.stream()
                        .map(discordRoleService::getRoleName)
                        .toList();
            }
        }

        return new ClanmemberViewData(discordUserName, roleNames, discordAvatarUrl);
    }

    public void saveNewClanmember(NewClanmemberDTO dto) {

        ClanmemberEntity newMember = new ClanmemberEntity();

        newMember.setDiscordId(dto.getDiscordId());
        newMember.setDiscordName(dto.getDiscordName());
        newMember.setPlayerNickname(dto.getPlayerNickname());
        newMember.setIngameName(dto.getIngameName());

        newMember.setClanRank(dto.getClanRank() != null ? dto.getClanRank().name() : ClanRank.SOLDIER.name());

        newMember.setAvatarHash(dto.getAvatarHash());
        newMember.setDiscordRoles(dto.getDiscordRoles());
        newMember.setChampions(List.of());

        clanmemberRepository.save(newMember);
        log.info("New Clanmember added to roster with Discord ID: {}", dto.getDiscordId());
    }

    public NewClanmemberDTO lookupDiscordUser(String userId) throws RuntimeException {
        String apiUri = DISCORD_MEMBER_API_BASE + clanServerId + "/members/" + userId;
        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setDiscordId(userId);

        try {
            String memberJson = webClient.get()
                    .uri(apiUri)
                    .header("Authorization", "Bot " + botToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode memberData = objectMapper.readTree(memberJson);
            JsonNode userData = memberData.path("user");

            String globalName = userData.path("global_name").asText();
            String username = memberData.path("username").asText();
            String nick = memberData.path("nick").asText();
            String avatarHash = userData.path("avatar").asText();

            dto.setDiscordName(globalName.isBlank() ? username : globalName);
            dto.setPlayerNickname(nick.isBlank() ? dto.getDiscordName() : nick);
            dto.setAvatarHash(avatarHash);

            List<String> currentDiscordRoles = getDiscordRoles(userId);
            dto.setDiscordRoles(currentDiscordRoles);

            return dto;

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("User {} not found in guild {}.", userId, clanServerId);
                throw new RuntimeException("Discord User ID not found in the clan server.");
            }
            log.error("WebClient error fetching Discord member data: {}", e.getResponseBodyAsString());
            throw new RuntimeException("API error while fetching Discord data.");
        } catch (Exception e) {
            log.error("General error in lookupDiscordUser: {}", e.getMessage());
            throw new RuntimeException("Lookup failed: " + e.getMessage());
        }
    }

    public boolean isDiscordIdInRoster(String discordId) {
        return clanmemberRepository.countByDiscordId(discordId) > 0;
    }

    public boolean isPlayerIngameNameInUse(String ingameName) {
        return clanmemberRepository.existsByIngameName(ingameName);
    }

    private List<String> getDiscordRoles(String userId) throws RuntimeException {
        String apiUri = DISCORD_MEMBER_API_BASE + clanServerId + "/members/" + userId;

        try {
            String memberJson = webClient.get()
                    .uri(apiUri)
                    .header("Authorization", "Bot " + botToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode memberData = objectMapper.readTree(memberJson);

            JsonNode rolesNode = memberData.path("roles");

            if (rolesNode.isArray()) {
                List<String> roles = new java.util.ArrayList<>();
                for (JsonNode roleId : rolesNode) {
                    roles.add(roleId.asText());
                }
                return roles;
            }

            return List.of();

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("User {} not found in guild {}. Cannot fetch roles.", userId, clanServerId);
                return List.of();
            }
            log.error("WebClient error fetching Discord member roles: {}", e.getResponseBodyAsString());
            throw new RuntimeException("API error while fetching Discord roles.");
        } catch (Exception e) {
            log.error("General error in getDiscordRoles: {}", e.getMessage());
            throw new RuntimeException("Role lookup failed: " + e.getMessage());
        }
    }
}
