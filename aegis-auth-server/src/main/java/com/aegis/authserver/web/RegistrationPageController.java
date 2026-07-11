package com.aegis.authserver.web;

import java.util.Set;

import com.aegis.authserver.account.PasswordPolicy;
import com.aegis.authserver.user.RegistrationService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Browser-facing account registration — the HTML sibling of the JSON
 * {@link RegistrationController} ({@code POST /api/register}). Both delegate to
 * {@link RegistrationService}, so the password policy, username uniqueness, and
 * audit trail are enforced identically regardless of the entry point.
 */
@Controller
@RequestMapping("/register")
public class RegistrationPageController {

    private final RegistrationService registrationService;

    public RegistrationPageController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("minPasswordLength", PasswordPolicy.MIN_LENGTH);
        return "register";
    }

    @PostMapping
    public String register(@RequestParam String username, @RequestParam String password,
            @RequestParam String confirmPassword, Model model) {
        String trimmed = username.trim();
        try {
            if (trimmed.isEmpty() || trimmed.length() > 100) {
                throw new IllegalArgumentException("Username must be between 1 and 100 characters");
            }
            if (!password.equals(confirmPassword)) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            registrationService.register(trimmed, password, Set.of("USER"));
        } catch (IllegalArgumentException | RegistrationService.UsernameTakenException
                | PasswordPolicy.WeakPasswordException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("username", trimmed);
            model.addAttribute("minPasswordLength", PasswordPolicy.MIN_LENGTH);
            return "register";
        }
        return "redirect:/login?registered";
    }
}
