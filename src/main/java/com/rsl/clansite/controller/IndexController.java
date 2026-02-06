package com.rsl.clansite.controller;

import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.DiscordRoleService;
import com.rsl.clansite.service.SiteAssetService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;

@Controller
public class IndexController {
    private final CommonsService commonsService;
    private final SiteAssetService siteAssetService;

    public IndexController(final CommonsService commonsService,
                           final SiteAssetService siteAssetService) {
        this.commonsService = commonsService;
        this.siteAssetService = siteAssetService;
    }

    @GetMapping(value={"", "/", "/index"})
    public String index(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            Boolean needsWarning = oauth2User.getAttribute("needsRoleWarning");
            if (Boolean.TRUE.equals(needsWarning)) {
                model.addAttribute("roleWarning", "You are logged in! However, it seems like you don't have the right roles in the Discord Server. Therefore we did not give you full access to the website yet. Please ask an admin to give you the right roles in the Discord Server and come back here!");
            }

            model.addAttribute("isInBeta", false);

            String discordID = oauth2User.getAttribute("id");
            if (DiscordRoleService.listOfDiscordIDsForBeta.contains(discordID)) {
                model.addAttribute("isInBeta", true);
            }
        }

        return "index";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            Model model,
                            Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/profile";
        }
        commonsService.fillModel(model, authentication);

        if (error != null) {
            model.addAttribute("loginError", error);
        }
        return "login";
    }

    @GetMapping("/favicon.ico")
    @ResponseBody
    public ResponseEntity<byte[]> getFavicon() {
        return siteAssetService.getFavicon()
                .map(asset -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(asset.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                        .body(asset.getData()))
                .orElse(ResponseEntity.notFound().build());
    }
}