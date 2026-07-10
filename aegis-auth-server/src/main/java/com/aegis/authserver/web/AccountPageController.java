package com.aegis.authserver.web;

import java.security.Principal;

import com.aegis.authserver.mfa.MfaEnrollmentService;
import com.aegis.authserver.mfa.QrSvgRenderer;
import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.AppUserRepository;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Self-service account page for the signed-in user: identity at a glance plus
 * browser-based TOTP enrollment (QR code → confirm a code → MFA on). The HTML
 * sibling of {@code /api/mfa/**} — both run through {@link MfaEnrollmentService},
 * so the two-step activation rule and the audit trail are identical.
 */
@Controller
@RequestMapping("/account")
public class AccountPageController {

    private final AppUserRepository users;
    private final MfaEnrollmentService enrollment;

    public AccountPageController(AppUserRepository users, MfaEnrollmentService enrollment) {
        this.users = users;
        this.enrollment = enrollment;
    }

    @GetMapping
    public String account(Principal principal, Model model) {
        AppUser user = require(principal);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("roles", user.getRoles());
        model.addAttribute("mfaEnabled", user.isMfaEnabled());
        model.addAttribute("mfaPending", !user.isMfaEnabled() && user.getMfaSecret() != null);
        return "account";
    }

    @PostMapping("/mfa/enroll")
    public String enroll(Principal principal) {
        enrollment.enroll(principal.getName());
        return "redirect:/account/mfa";
    }

    @GetMapping("/mfa")
    public String mfaSetup(Principal principal, Model model) {
        return enrollment.pending(principal.getName())
                .map(pending -> {
                    addSetupModel(model, pending);
                    return "account-mfa";
                })
                .orElse("redirect:/account"); // nothing pending (not enrolled, or already active)
    }

    @PostMapping("/mfa/activate")
    public String activate(Principal principal, @RequestParam String code,
            Model model, RedirectAttributes redirect) {
        if (enrollment.activate(principal.getName(), code)) {
            redirect.addFlashAttribute("mfaActivated", true);
            return "redirect:/account";
        }
        return enrollment.pending(principal.getName())
                .map(pending -> {
                    addSetupModel(model, pending);
                    model.addAttribute("error", "That code did not match — check the app and try again.");
                    return "account-mfa";
                })
                .orElse("redirect:/account");
    }

    private void addSetupModel(Model model, MfaEnrollmentService.PendingEnrollment pending) {
        model.addAttribute("secret", pending.secret());
        model.addAttribute("qrSvg", QrSvgRenderer.render(pending.otpauthUri()));
    }

    private AppUser require(Principal principal) {
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException(principal.getName()));
    }
}
