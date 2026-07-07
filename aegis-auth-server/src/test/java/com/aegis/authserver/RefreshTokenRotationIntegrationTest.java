package com.aegis.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Security test (Phase 5): refresh-token <b>rotation</b> and <b>replay detection</b>.
 *
 * <p>The web client is configured with {@code reuseRefreshTokens(false)}, so each refresh
 * mints a brand-new refresh token and invalidates the old one. This test drives the full
 * Authorization-Code + PKCE flow against a real auth server (Testcontainers Postgres), then:
 * <ol>
 *   <li>refreshes once — expecting a <em>different</em> refresh token back (rotation), and</li>
 *   <li>replays the <em>original</em> refresh token — expecting {@code 400 invalid_grant}
 *       (a stolen, already-used refresh token is worthless).</li>
 * </ol>
 *
 * <p>Named {@code *IntegrationTest} so it is skipped in local runs without Docker; it runs
 * in CI where the Linux runner provides Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RefreshTokenRotationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final String CLIENT_ID = "aegis-web-client";
    private static final String REDIRECT_URI = "http://127.0.0.1:8080/login/oauth2/code/aegis";

    @LocalServerPort
    int port;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)   // we read Location headers ourselves
            .build();

    @Test
    void rotatesRefreshTokenAndRejectsReplayOfTheOldOne() throws Exception {
        String verifier = randomUrlSafe(64);
        String challenge = s256(verifier);

        login();
        String code = authorize(challenge);
        String firstRefresh = extract(exchangeCode(code, verifier), "refresh_token");

        // (1) Rotation: refreshing returns a NEW refresh token.
        String rotatedBody = refresh(firstRefresh, 200);
        String secondRefresh = extract(rotatedBody, "refresh_token");
        assertThat(secondRefresh).isNotBlank().isNotEqualTo(firstRefresh);

        // (2) Replay: the ORIGINAL refresh token is now invalid.
        String replayBody = refresh(firstRefresh, 400);
        assertThat(replayBody).contains("invalid_grant");
    }

    // --- flow steps ------------------------------------------------------------

    /** Form-login as the seeded admin, carrying the CSRF token and session cookie. */
    private void login() throws Exception {
        String loginPage = get("/login").body();
        String csrf = extractCsrf(loginPage);
        String form = "username=admin&password=changeit&_csrf=" + enc(csrf);

        HttpResponse<String> res = http.send(HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        // Successful form login is a 302 (to the saved request / "/"). A 200 means the login
        // page was re-rendered with an error.
        assertThat(res.statusCode()).as("login should redirect on success").isEqualTo(302);
    }

    /** Hit the authorize endpoint (consent not required) and pull the code from the redirect. */
    private String authorize(String codeChallenge) throws Exception {
        String query = "response_type=code"
                + "&client_id=" + CLIENT_ID
                + "&scope=" + enc("openid profile")
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256"
                + "&state=xyz";
        HttpResponse<String> res = get("/oauth2/authorize?" + query);
        assertThat(res.statusCode()).as("authorize should redirect with a code").isEqualTo(302);

        String location = res.headers().firstValue("Location").orElseThrow();
        Matcher m = Pattern.compile("[?&]code=([^&]+)").matcher(location);
        assertThat(m.find()).as("redirect must contain a code: " + location).isTrue();
        return m.group(1);
    }

    private String exchangeCode(String code, String codeVerifier) throws Exception {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&client_id=" + CLIENT_ID
                + "&code_verifier=" + enc(codeVerifier);
        HttpResponse<String> res = postToken(form);
        assertThat(res.statusCode()).as("code exchange should succeed: " + res.body()).isEqualTo(200);
        return res.body();
    }

    private String refresh(String refreshToken, int expectedStatus) throws Exception {
        String form = "grant_type=refresh_token"
                + "&refresh_token=" + enc(refreshToken)
                + "&client_id=" + CLIENT_ID;
        HttpResponse<String> res = postToken(form);
        assertThat(res.statusCode()).as("refresh status; body=" + res.body()).isEqualTo(expectedStatus);
        return res.body();
    }

    // --- http helpers ----------------------------------------------------------

    private HttpResponse<String> postToken(String form) throws Exception {
        // Public client (PKCE) — no client authentication header; token endpoint is CSRF-exempt.
        return http.send(HttpRequest.newBuilder(uri("/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    // --- parsing helpers -------------------------------------------------------

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as("field '" + field + "' present in: " + json).isTrue();
        return m.group(1);
    }

    private static String extractCsrf(String html) {
        // The login form renders <input name="_csrf" value="..."/> (attribute order may vary).
        Matcher a = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        if (a.find()) {
            return a.group(1);
        }
        Matcher b = Pattern.compile("value=\"([^\"]+)\"[^>]*name=\"_csrf\"").matcher(html);
        assertThat(b.find()).as("CSRF token present on login page").isTrue();
        return b.group(1);
    }

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
