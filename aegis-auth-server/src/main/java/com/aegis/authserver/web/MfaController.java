package com.aegis.authserver.web;

import java.security.Principal;
import java.util.Map;

import com.aegis.authserver.mfa.MfaEnrollmentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API for TOTP enrollment of the authenticated user. Thin shell over
 * {@link MfaEnrollmentService} (shared with the {@code /account/mfa} page) — see the
 * service for the two-step enroll/activate rationale.
 */
@RestController
@RequestMapping("/api/mfa")
public class MfaController {

    private final MfaEnrollmentService enrollment;

    public MfaController(MfaEnrollmentService enrollment) {
        this.enrollment = enrollment;
    }

    @PostMapping("/enroll")
    public Map<String, String> enroll(Principal principal) {
        MfaEnrollmentService.PendingEnrollment pending = enrollment.enroll(principal.getName());
        return Map.of("secret", pending.secret(), "otpauthUri", pending.otpauthUri());
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, String>> activate(Principal principal,
                                                         @Valid @RequestBody OtpRequest request) {
        if (!enrollment.activate(principal.getName(), request.code())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid code"));
        }
        return ResponseEntity.ok(Map.of("status", "MFA enabled"));
    }

    @ExceptionHandler(MfaEnrollmentService.NotEnrolledException.class)
    public ResponseEntity<Map<String, String>> handleNotEnrolled() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Call /enroll first"));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Void> handleUnknownUser() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    public record OtpRequest(@NotBlank String code) {
    }
}
