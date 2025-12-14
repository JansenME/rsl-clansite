package com.rsl.clansite.model;

import lombok.Data;

import java.util.List;

@Data
public class ClanmemberViewData {
    private final String discordUserName;
    private final List<String> discordUserRoles;
}
