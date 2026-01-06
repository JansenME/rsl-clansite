package com.rsl.clansite.controller;

import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiteAssetService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
