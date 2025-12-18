package com.rsl.clansite.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ClanmemberViewData implements Serializable {
    private final String discordUserName;
    private final List<String> discordUserRoles;
    private final String discordAvatarUrl;
}
