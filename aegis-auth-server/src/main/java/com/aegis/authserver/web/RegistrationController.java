package com.aegis.authserver.web;

import java.util.Map;
import java.util.Set;

import com.aegis.authserver.account.PasswordPolicy;
import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.RegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account registration. Public endpoint (allow-listed in security config);
 * the password policy and username-uniqueness are enforced by {@link RegistrationService}.
 */
@RestController
@RequestMapping("/api/register")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegistrationRequest request) {
        AppUser user = registrationService.register(
                request.username(), request.password(), Set.of("USER"));
        return Map.of("id", user.getId(), "username", user.getUsername());
    }

    @ExceptionHandler(RegistrationService.UsernameTakenException.class)
    public ResponseEntity<Map<String, String>> handleTaken(RegistrationService.UsernameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PasswordPolicy.WeakPasswordException.class)
    public ResponseEntity<Map<String, String>> handleWeak(PasswordPolicy.WeakPasswordException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    /** Registration payload. Password strength beyond length is checked server-side. */
    public record RegistrationRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = 200) String password) {
    }
}
