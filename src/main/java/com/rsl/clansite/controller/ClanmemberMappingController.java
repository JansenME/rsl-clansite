package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.BulkMapRequest;
import com.rsl.clansite.model.dto.ClanmemberMappingDto;
import com.rsl.clansite.service.ClanmemberMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/app/v1/roster")
@RequiredArgsConstructor
public class ClanmemberMappingController {
    private final ClanmemberMappingService mappingService;

    @PostMapping("/sync-member")
    public ResponseEntity<Void> syncMember(@RequestBody ClanmemberMappingDto dto) {
        mappingService.syncMember(dto.getPlariumId(), dto.getPlayerName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/mapping")
    public ResponseEntity<Map<Long, String>> getRosterMapping() {
        Map<Long, String> mapping = mappingService.getRosterMapping();
        return ResponseEntity.ok(mapping);
    }

    @PostMapping("/bulk-map")
    public ResponseEntity<Void> bulkMapMembers(@RequestBody BulkMapRequest request) {
        mappingService.bulkMapMembers(request.getMappings());
        return ResponseEntity.ok().build();
    }
}
