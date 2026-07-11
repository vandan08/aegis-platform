package com.aegis.authserver.web;

import java.util.List;

import com.aegis.authserver.audit.AuditChainVerifier;
import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.audit.AuthAuditEvent;
import com.aegis.authserver.audit.AuthAuditEventRepository;
import com.aegis.authserver.audit.ChainVerificationResult;
import com.aegis.authserver.user.RegistrationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4.x: slice moved out of spring-boot-test-autoconfigure (see pom note)
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every Thymeleaf page through the real view layer (no database, no Docker):
 * catches template syntax errors, missing model attributes, and broken fragment
 * references at test time instead of first page load. Also pins the admin pages'
 * access rules. The security chain here mirrors the real one where it matters
 * (custom login page, /admin/** needs ROLE_ADMIN, CSRF on).
 */
@WebMvcTest(controllers = {LoginController.class, RegistrationPageController.class,
        AuditTrailPageController.class, AccountPageController.class})
// Boot 4.x split security auto-config into the spring-boot-security module, and the
// @WebMvcTest slice no longer pulls it in — import it explicitly so the test filter
// chain (and the CSRF request attribute the templates render) exists.
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class UiPagesRenderTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegistrationService registrationService;
    @MockitoBean
    private AuthAuditEventRepository auditEvents;
    @MockitoBean
    private AuditChainVerifier verifier;
    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private com.aegis.authserver.user.AppUserRepository appUsers;
    @MockitoBean
    private com.aegis.authserver.mfa.MfaEnrollmentService mfaEnrollment;

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .requestMatchers("/account/**").authenticated()
                            .anyRequest().permitAll())
                    .formLogin(form -> form.loginPage("/login"));
            return http.build();
        }
    }

    @Test
    @DisplayName("login page renders with the MFA field and a link to registration")
    void loginPageRenders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "Sign in", "One-time code", "Create an account")));
    }

    @Test
    @DisplayName("post-registration login page shows the success message")
    void loginPageShowsRegisteredMessage() throws Exception {
        mvc.perform(get("/login").queryParam("registered", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Account created — sign in to continue.")));
    }

    @Test
    @DisplayName("registration page renders with the password-policy hint")
    void registerPageRenders() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "At least 12 characters, with a letter and a digit.")));
    }

    @Test
    @DisplayName("successful registration redirects to login with the success flag")
    void registerSuccessRedirects() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("password", "correct-horse-9")
                        .param("confirmPassword", "correct-horse-9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
        verify(registrationService).register(eq("alice"), eq("correct-horse-9"), any());
    }

    @Test
    @DisplayName("mismatched passwords re-render the form with the error and keep the username")
    void registerMismatchShowsError() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("password", "correct-horse-9")
                        .param("confirmPassword", "different-horse-9"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "Passwords do not match", "value=\"alice\"")));
    }

    @Test
    @DisplayName("service-side rejection (weak password) surfaces on the form")
    void registerWeakPasswordShowsError() throws Exception {
        when(registrationService.register(anyString(), anyString(), any()))
                .thenThrow(new com.aegis.authserver.account.PasswordPolicy.WeakPasswordException(
                        "Password must contain at least one letter and one digit"));
        mvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("password", "aaaaaaaaaaaa")
                        .param("confirmPassword", "aaaaaaaaaaaa"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Password must contain at least one letter and one digit")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("audit page renders chained and pre-chain events for an admin")
    void auditPageRendersForAdmin() throws Exception {
        AuthAuditEvent legacy = new AuthAuditEvent("LOGIN_SUCCESS", "old-user", "pre-V6 row", null);
        ReflectionTestUtils.setField(legacy, "id", 1L);
        AuthAuditEvent chained = new AuthAuditEvent("LOGIN_FAILURE", "mallory", "bad password", "10.6.6.6");
        ReflectionTestUtils.setField(chained, "id", 2L);
        ReflectionTestUtils.setField(chained, "prevHash", "0".repeat(64));
        ReflectionTestUtils.setField(chained, "entryHash", "ab12cd34ef56" + "0".repeat(52));
        when(auditEvents.findTop50ByOrderByOccurredAtDesc()).thenReturn(List.of(chained, legacy));

        mvc.perform(get("/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "Audit trail", "Verify chain integrity",
                        "LOGIN_FAILURE", "mallory", "ab12cd34ef56…",
                        "LOGIN_SUCCESS", "pre-chain")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("verify button re-checks the chain, audits the check, and redirects back")
    void verifyPostRunsAndRedirects() throws Exception {
        when(verifier.verify()).thenReturn(
                new ChainVerificationResult(true, 41, 2, "c0ffee" + "0".repeat(58), null, null));

        mvc.perform(post("/admin/audit/verify").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/audit"));
        verify(auditService).record(eq(AuditEventType.AUDIT_CHAIN_VERIFIED), eq("admin"), anyString());
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    @DisplayName("non-admin users get 403 on the audit page")
    void auditPageForbiddenForNonAdmin() throws Exception {
        mvc.perform(get("/admin/audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("account page shows identity and offers to enable MFA when it's off")
    void accountPageRendersMfaOff() throws Exception {
        com.aegis.authserver.user.AppUser alice = new com.aegis.authserver.user.AppUser("alice", "{noop}x");
        alice.getRoles().add("USER");
        when(appUsers.findByUsername("alice")).thenReturn(java.util.Optional.of(alice));

        mvc.perform(get("/account"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "Your account", "alice", "USER",
                        "Enable two-factor authentication")));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("MFA setup page renders the QR SVG and the manual secret")
    void mfaSetupPageRendersQr() throws Exception {
        when(mfaEnrollment.pending("alice")).thenReturn(java.util.Optional.of(
                new com.aegis.authserver.mfa.MfaEnrollmentService.PendingEnrollment(
                        "JBSWY3DPEHPK3PXP",
                        "otpauth://totp/Aegis:alice?secret=JBSWY3DPEHPK3PXP&issuer=Aegis")));

        mvc.perform(get("/account/mfa"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "Scan with your authenticator app", "<svg", "</svg>",
                        "JBSWY3DPEHPK3PXP", "Activate MFA")));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("a wrong activation code re-renders setup with the error")
    void mfaActivateWrongCodeShowsError() throws Exception {
        when(mfaEnrollment.activate("alice", "111111")).thenReturn(false);
        when(mfaEnrollment.pending("alice")).thenReturn(java.util.Optional.of(
                new com.aegis.authserver.mfa.MfaEnrollmentService.PendingEnrollment(
                        "JBSWY3DPEHPK3PXP",
                        "otpauth://totp/Aegis:alice?secret=JBSWY3DPEHPK3PXP&issuer=Aegis")));

        mvc.perform(post("/account/mfa/activate").with(csrf()).param("code", "111111"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "That code did not match")));
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("a valid activation code redirects to the account page")
    void mfaActivateValidCodeRedirects() throws Exception {
        when(mfaEnrollment.activate("alice", "222222")).thenReturn(true);

        mvc.perform(post("/account/mfa/activate").with(csrf()).param("code", "222222"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"));
    }

    @Test
    @DisplayName("the account page requires sign-in")
    void accountPageRedirectsAnonymous() throws Exception {
        mvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("anonymous users are sent to the login page")
    void auditPageRedirectsAnonymous() throws Exception {
        mvc.perform(get("/admin/audit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
