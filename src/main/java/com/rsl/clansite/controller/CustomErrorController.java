package com.rsl.clansite.controller;

import com.rsl.clansite.service.CommonsService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CustomErrorController implements ErrorController {
    private final CommonsService commonsService;

    public CustomErrorController(CommonsService commonsService) {
        this.commonsService = commonsService;
    }

    @GetMapping("/login-error/unlinked")
    public String handleUnlinkedAccountError(@RequestParam("error") String errorMessage, Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("contact", "Please contact the administrator on Discord to link your account.");
        return "unlinked";
    }

    @GetMapping("/login-error")
    public String handleGenericLoginError(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);
        model.addAttribute("errorMessage", "An unknown authentication error occurred.");
        return "error";
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        commonsService.fillModel(model, auth);

        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == 404) return "redirect:/error/404";
            if (statusCode == 403) return "redirect:/error/403";
            if (statusCode == 500) return "redirect:/error/500";

        }

        return "error/generic";
    }

    @GetMapping("/error/403")
    public String handleForbidden(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        commonsService.fillModel(model, auth);

        model.addAttribute("errorTitle", "403 - Access Denied");
        model.addAttribute("errorMessage", "The hell you doing here? You can't access this! Get out!");
        return "error/403";
    }

    @GetMapping("/error/404")
    public String handleNotFound(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        commonsService.fillModel(model, auth);

        model.addAttribute("errorTitle", "404 - Page Not Found");
        model.addAttribute("errorMessage", "Yeah this doesn't exist. Just go away...");
        return "error/404";
    }

    @GetMapping("/error/500")
    public String handleServerError(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        commonsService.fillModel(model, auth);

        model.addAttribute("errorTitle", "500 - Server Disturbance");
        model.addAttribute("errorMessage", "Woopsie, something went wrong. No idea what though. So good luck with that and have a nice day!");
        return "error/500";
    }
}
