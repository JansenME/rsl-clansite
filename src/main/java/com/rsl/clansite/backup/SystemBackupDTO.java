package com.rsl.clansite.backup;

import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class SystemBackupDTO {
    private String version = "1.0";
    private LocalDateTime timestamp = LocalDateTime.now();

    private List<ChampionEntity> champions;
    private List<ClanmemberEntity> clanmembers;

    public SystemBackupDTO(List<ChampionEntity> champions, List<ClanmemberEntity> clanmembers) {
        this.champions = champions;
        this.clanmembers = clanmembers;
    }
}
