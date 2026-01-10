package com.rsl.clansite.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatusDTO {
    private String memberId;
    private String discordId;
    private String ingameName;

    private String discordNickname;

    private boolean nicknameSynced;
    private boolean rolesSynced;
    private boolean avatarSynced;

    private String statusMessage;
}
