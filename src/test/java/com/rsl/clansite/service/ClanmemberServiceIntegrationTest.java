package com.rsl.clansite.service;

import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.model.enums.MemberStatus;
import com.rsl.clansite.repository.AuditLogRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ClanmemberServiceIntegrationTest {
    @Autowired
    private ClanmemberService clanmemberService;

    @Autowired
    private ClanmemberRepository clanmemberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private DiscordRoleService discordRoleService;

    @BeforeEach
    void cleanUp() {
        clanmemberRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Integration: Save Member should persist to DB and create Audit Log")
    void testSaveMemberFlow() {
        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setDiscordId("IntegrationID");
        dto.setIngameName("IntegrationPlayer");
        dto.setClanGroup(ClanGroup.T1);
        dto.setClanRank(ClanRank.SOLDIER);

        Authentication mockAuth = createMockAuthentication("AdminUser");

        clanmemberService.saveNewClanmember(dto, mockAuth);

        List<ClanmemberEntity> members = clanmemberRepository.findAll();
        assertEquals(1, members.size());
        assertEquals("IntegrationPlayer", members.get(0).getIngameName());

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals(AuditAction.MEMBER_ADD, logs.get(0).getAction());
        assertEquals("IntegrationPlayer", logs.get(0).getTarget());
        assertEquals("AdminUser", logs.get(0).getActorDiscordName());
    }

    @Test
    @DisplayName("Integration: FindAll should return Sorted List from Real DB")
    void testSortingFromDatabase() {
        createAndSaveMember("Soldier", ClanRank.SOLDIER);
        createAndSaveMember("Leader", ClanRank.LEADER);
        createAndSaveMember("Deputy", ClanRank.DEPUTY);

        List<ClanmemberEntity> result = clanmemberService.findAllClanmemberEntities();

        assertEquals(3, result.size());
        assertEquals(ClanRank.LEADER, result.get(0).getClanRank());
        assertEquals(ClanRank.DEPUTY, result.get(1).getClanRank());
        assertEquals(ClanRank.SOLDIER, result.get(2).getClanRank());
    }

    @Test
    @DisplayName("Integration: Delete should remove from DB and Log")
    void testDeleteFlow() {
        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordName("ToBeDeleted");
        member.setClanRank(ClanRank.SOLDIER);
        ClanmemberEntity saved = clanmemberRepository.save(member);
        String savedId = saved.getId().toHexString();

        auditLogRepository.deleteAll();

        Authentication mockAuth = createMockAuthentication("AdminUser");
        MockHttpSession mockSession = new MockHttpSession();

        clanmemberService.deleteById(savedId, mockSession, mockAuth);

        assertTrue(clanmemberRepository.existsById(new ObjectId(savedId)));

        ClanmemberEntity memberFromDB = clanmemberRepository.findAllByDiscordId(member.getDiscordId()).get(0);
        assertEquals(MemberStatus.INACTIVE, memberFromDB.getStatus());

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size(), "One audit log should have been created");
        assertEquals(AuditAction.MEMBER_DELETE, logs.get(0).getAction());
    }

    private Authentication createMockAuthentication(String username) {
        Map<String, Object> attributes = Map.of(
                "id", "999",
                "global_name", username
        );
        OAuth2User principal = new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN")),
                attributes,
                "global_name"
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private ClanmemberEntity createAndSaveMember(String name, ClanRank rank) {
        ClanmemberEntity entity = new ClanmemberEntity();
        entity.setIngameName(name);
        entity.setClanRank(ClanRank.valueOf(rank.name()));
        return clanmemberRepository.save(entity);
    }
}
