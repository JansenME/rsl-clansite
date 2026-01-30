package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.RosterSyncService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/sync")
public class RosterSyncController {

    private final CommonsService commonsService;
    private final RosterSyncService rosterSyncService;
    private final ClanmemberService clanmemberService;
    private final ClanmemberRepository clanmemberRepository;

    public RosterSyncController(CommonsService commonsService,
                                RosterSyncService rosterSyncService,
                                ClanmemberService clanmemberService,
                                ClanmemberRepository clanmemberRepository) {
        this.commonsService = commonsService;
        this.rosterSyncService = rosterSyncService;
        this.clanmemberService = clanmemberService;
        this.clanmemberRepository = clanmemberRepository;
    }

    // ... [showUploadPage, handleFileUpload, showPreview methods REMAIN THE SAME] ...

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String showUploadPage(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        if (activeMember == null || activeMember.getDiscordId() == null) {
            model.addAttribute("errorMessage", "You must have a linked Discord account to use this feature.");
            return "redirect:/profile";
        }

        LocalDateTime lastModified = rosterSyncService.getFileLastModified(activeMember.getDiscordId());
        if (lastModified != null) {
            model.addAttribute("resumeAvailable", true);
            model.addAttribute("fileLastModified", lastModified);
        }

        return "roster-sync-upload";
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   Authentication authentication,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {

        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        if (activeMember == null) return "redirect:/login";

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a file to upload.");
            return "redirect:/sync";
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid file type. Please upload a .csv file from RSL Helper.");
            return "redirect:/sync";
        }

        try {
            rosterSyncService.saveUpload(file, activeMember.getDiscordId());
            return "redirect:/sync/preview";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Upload failed: " + e.getMessage());
            return "redirect:/sync";
        }
    }

    @GetMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    public String showPreview(Model model, Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        commonsService.fillModel(model, authentication, session);

        ClanmemberEntity sessionMember = clanmemberService.getActiveClanmember(session, authentication);
        if (sessionMember == null) return "redirect:/login";

        ClanmemberEntity activeMember = clanmemberRepository.findById(sessionMember.getId()).orElse(sessionMember);

        try {
            if (rosterSyncService.getExistingSyncFile(activeMember.getDiscordId()) == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "No uploaded file found. Please upload your RSL Helper CSV.");
                return "redirect:/sync";
            }

            RosterSyncService.SyncDiffResult diff = rosterSyncService.generateDiff(activeMember.getDiscordId(), activeMember);
            model.addAttribute("diff", diff);
            return "roster-sync-preview";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error processing file: " + e.getMessage());
            return "redirect:/sync";
        }
    }

    // --- Phase 5: Final Commit & Cancel ---

    @PostMapping("/commit")
    @PreAuthorize("isAuthenticated()")
    public String commitSync(@RequestParam(required = false) List<Integer> selectedIndices,
                             Authentication authentication,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        ClanmemberEntity sessionMember = clanmemberService.getActiveClanmember(session, authentication);
        if (sessionMember == null) return "redirect:/login";

        ClanmemberEntity activeMember = clanmemberRepository.findById(sessionMember.getId()).orElse(sessionMember);

        try {
            rosterSyncService.applySync(activeMember.getDiscordId(), activeMember, selectedIndices, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Roster synchronization completed successfully!");
            return "redirect:/profile/" + activeMember.getId().toHexString();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Sync failed: " + e.getMessage());
            return "redirect:/sync/preview";
        }
    }

    @GetMapping("/cancel")
    @PreAuthorize("isAuthenticated()")
    public String cancelSync(Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        if (activeMember != null) {
            rosterSyncService.deleteSyncFile(activeMember.getDiscordId());
        }
        redirectAttributes.addFlashAttribute("infoMessage", "Sync cancelled and file discarded.");
        return "redirect:/profile";
    }
}