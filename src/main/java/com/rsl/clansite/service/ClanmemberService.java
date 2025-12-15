package com.rsl.clansite.service;

import com.rsl.clansite.exceptions.UnlinkedAccountException;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ClanmemberService {
    private final ClanmemberRepository clanmemberRepository;
    private final DiscordRoleService discordRoleService;

    @Autowired
    public ClanmemberService(ClanmemberRepository clanmemberRepository, final DiscordRoleService discordRoleService) {
        this.clanmemberRepository = clanmemberRepository;
        this.discordRoleService = discordRoleService;
    }

    public void linkClanmember(final String discordId, final String globalName, final String avatarHash, final List<String> roles) {
        List<ClanmemberEntity> linkedMembers = clanmemberRepository.findAllByDiscordId(discordId);

        if (linkedMembers.isEmpty()) {
            throw new UnlinkedAccountException("User's Discord ID is not linked.");
        }

        final List<String> masterOrder = discordRoleService.getOrderedRoleIds();
        List<String> sortedRoles = new java.util.ArrayList<>(roles);

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
}
