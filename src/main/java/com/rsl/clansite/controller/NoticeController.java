package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.NoticeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/notices")
public class NoticeController {

    private final NoticeService noticeService;
    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;

    public NoticeController(NoticeService noticeService,
                            CommonsService commonsService,
                            ClanmemberService clanmemberService) {
        this.noticeService = noticeService;
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String viewNoticeDashboard(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        model.addAttribute("notices", noticeService.getAllNotices());

        return "admin-notices";
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createNotice(@RequestParam("title") String title,
                               @RequestParam("content") String content,
                               Authentication authentication) {

        String discordId = authentication.getName();
        String author = "Admin"; // Default fallback

        // Resolve the friendly name from the database
        List<ClanmemberEntity> members = clanmemberService.getLinkedClanmembers(discordId);
        if (!members.isEmpty()) {
            ClanmemberEntity admin = members.get(0);
            // Prefer the Nickname set in the Roster, otherwise use the Discord Name
            if (admin.getPlayerNickname() != null && !admin.getPlayerNickname().isBlank()) {
                author = admin.getPlayerNickname();
            } else if (admin.getDiscordName() != null) {
                author = admin.getDiscordName();
            }
        }

        noticeService.createNotice(title, content, author, authentication);

        return "redirect:/admin/notices";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public String toggleNoticeStatus(@PathVariable String id) {
        noticeService.toggleActive(id);
        return "redirect:/admin/notices";
    }
}