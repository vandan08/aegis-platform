package com.aegis.authserver.web;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aegis.authserver.audit.AuditChainVerifier;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.audit.AuthAuditEvent;
import com.aegis.authserver.audit.AuthAuditEventRepository;
import com.aegis.authserver.audit.ChainVerificationResult;
import com.aegis.authserver.user.RegistrationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Not an assertion suite: renders each page through the real view layer and writes the
 * HTML (plus the stylesheet) to {@code target/ui-preview/} so the design can be eyeballed
 * in a browser without booting the full server (which needs Postgres). Regenerate with:
 * {@code mvnw -pl aegis-auth-server -Dtest=UiPreviewDumpTest test}, then open the files
 * or serve the directory statically.
 */
@WebMvcTest(controllers = {LoginController.class, RegistrationPageController.class,
        AuditTrailPageController.class, AccountPageController.class})
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class UiPreviewDumpTest {

    private static final Path OUT = Path.of("target", "ui-preview");

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
        SecurityFilterChain previewChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .formLogin(form -> form.loginPage("/login"));
            return http.build();
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dumpRenderedPages() throws Exception {
        Files.createDirectories(OUT);
        try (InputStream css = getClass().getResourceAsStream("/static/css/aegis.css")) {
            Files.write(OUT.resolve("aegis.css"), css.readAllBytes());
        }

        when(auditEvents.findTop50ByOrderByOccurredAtDesc()).thenReturn(sampleEvents());

        dump("login.html", get("/login"));
        dump("login-registered.html", get("/login").queryParam("registered", ""));
        dump("register.html", get("/register"));
        dump("admin-audit.html", get("/admin/audit"));
        dump("admin-audit-intact.html", get("/admin/audit")
                .flashAttr("verification", new ChainVerificationResult(
                        true, 5, 1, "3f9c2ab84d17e650b2c8a91f4e7d0c635a18b4f2d9e07c1a6b3f58e2d4a90c7b", null, null)));
        dump("admin-audit-broken.html", get("/admin/audit")
                .flashAttr("verification", new ChainVerificationResult(
                        false, 2, 1, "3f9c2ab84d17e650b2c8a91f4e7d0c635a18b4f2d9e07c1a6b3f58e2d4a90c7b",
                        4L, "entry hash mismatch (event content was altered)")));

        com.aegis.authserver.user.AppUser admin = new com.aegis.authserver.user.AppUser("admin", "{noop}x");
        admin.getRoles().add("USER");
        admin.getRoles().add("ADMIN");
        when(appUsers.findByUsername("admin")).thenReturn(java.util.Optional.of(admin));
        dump("account-mfa-off.html", get("/account"));
        admin.setMfaSecret("JBSWY3DPEHPK3PXP");
        admin.setMfaEnabled(true);
        dump("account-mfa-on.html", get("/account").flashAttr("mfaActivated", true));
        when(mfaEnrollment.pending("admin")).thenReturn(java.util.Optional.of(
                new com.aegis.authserver.mfa.MfaEnrollmentService.PendingEnrollment(
                        "JBSWY3DPEHPK3PXP",
                        "otpauth://totp/Aegis:admin?secret=JBSWY3DPEHPK3PXP&issuer=Aegis&algorithm=SHA1&digits=6&period=30")));
        dump("account-mfa-setup.html", get("/account/mfa"));
    }

    private void dump(String file, RequestBuilder request) throws Exception {
        String html = mvc.perform(request).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("/css/aegis.css", "aegis.css"); // relative so file:// / any static root works
        Files.writeString(OUT.resolve(file), html, StandardCharsets.UTF_8);
    }

    private List<AuthAuditEvent> sampleEvents() {
        String[][] rows = {
                {"AUDIT_CHAIN_VERIFIED", "admin", "chain intact, 5 events verified, head=3f9c2ab84d17", null},
                {"KEY_ROTATED", "admin", "new kid=7d3f1e9a-52c8", "192.168.1.10"},
                {"LOGIN_SUCCESS", "admin", "form login (mfa)", "192.168.1.10"},
                {"ACCOUNT_LOCKED", "mallory", "5 consecutive failures, locked 15m", "203.0.113.66"},
                {"LOGIN_FAILURE", "mallory", "bad password", "203.0.113.66"},
                {"USER_REGISTERED", "alice", "roles=[USER]", "192.168.1.22"},
        };
        long id = rows.length + 1;
        java.util.ArrayList<AuthAuditEvent> events = new java.util.ArrayList<>();
        for (String[] r : rows) {
            AuthAuditEvent e = new AuthAuditEvent(r[0], r[1], r[2], r[3]);
            ReflectionTestUtils.setField(e, "id", --id);
            if (id > 1) { // oldest row stays "pre-chain" to show the legacy rendering
                ReflectionTestUtils.setField(e, "prevHash", "0".repeat(64));
                ReflectionTestUtils.setField(e, "entryHash",
                        String.format("%08x", r[0].hashCode()).repeat(8));
            }
            events.add(e);
        }
        return events;
    }
}
