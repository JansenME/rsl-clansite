package com.rsl.clansite.service;

import com.rsl.clansite.model.ClanmemberViewData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class CommonsService {
    private static final DateTimeFormatter APP_DATE_FORMATTER = DateTimeFormatter.ofPattern("d-M-yyyy");

    private final ClanmemberService clanmemberService;
    private final BuildProperties buildProperties;

    public CommonsService(ClanmemberService clanmemberService,
                          @Autowired(required = false) BuildProperties buildProperties) {
        this.clanmemberService = clanmemberService;
        this.buildProperties = buildProperties;
    }

    public String generateImageFilename(String championName) {
        if (championName == null || championName.isEmpty()) {
            return "placeholder.png";
        }

        String cleanName = championName.toLowerCase().replaceAll("[^a-z0-9 ]", "");
        return cleanName.trim().replace(" ", "-") + ".png";
    }

    public void fillModel(Model model, Authentication authentication) {
        model.addAttribute("versionNumber", getAppVersion());
        model.addAttribute("currentYear", String.valueOf(Year.now().getValue()));
        model.addAttribute("applicationDate", getAppBuildDate());

        if (authentication != null && authentication.isAuthenticated()) {
            ClanmemberViewData viewData = clanmemberService.getUserViewData(authentication);
            model.addAttribute("clanmemberViewData", viewData);
        }
    }

    private String getAppVersion() {
        if (buildProperties != null) {
            return buildProperties.getVersion();
        }
        return "dev-local";
    }

    private String getAppBuildDate() {
        if (buildProperties != null && buildProperties.getTime() != null) {
            return buildProperties.getTime()
                    .atZone(ZoneId.of("Europe/Paris"))
                    .format(APP_DATE_FORMATTER);
        }
        return "Unknown Date";
    }
}
