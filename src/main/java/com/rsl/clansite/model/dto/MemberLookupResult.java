package com.rsl.clansite.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberLookupResult {
    private NewClanmemberDTO dto;
    private boolean success;
    private String warningMessage;
    private String errorMessage;

    public static MemberLookupResult success(NewClanmemberDTO dto, String warning) {
        return new MemberLookupResult(dto, true, warning, null);
    }

    public static MemberLookupResult failure(String error) {
        return new MemberLookupResult(new NewClanmemberDTO(), false, null, error);
    }
}
