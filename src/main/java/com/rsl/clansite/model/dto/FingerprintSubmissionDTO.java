package com.rsl.clansite.model.dto;

import lombok.Data;

@Data
public class FingerprintSubmissionDTO {
    private String championId;
    private Long hash;
}
