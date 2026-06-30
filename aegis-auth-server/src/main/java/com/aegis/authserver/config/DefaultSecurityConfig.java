package com.aegis.authserver.config;

import com.aegis.authserver.account.LockoutProperties;
import com.aegis.authserver.mfa.MfaAuthenticationDetailsSource;
import com.aegis.authserver.mfa.MfaAuthenticationProvider;
import com.aegis.authserver.mfa.TotpService;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for everything that is NOT an authorization-server protocol endpoint:
 * the login page, self-service registration, and MFA enrollment.
 *
 * <p><b>Phase 2:</b> users are loaded from Postgres ({@code JpaUserDetailsService}) and
 * authenticated by {@link MfaAuthenticationProvider}, which layers a TOTP second factor
 * on top of the password check. Account lockout is enforced via the {@code AppUser}
 * lockout fields and driven by authentication events.
 */
@Configuration
@EnableConfigurationProperties(LockoutProperties.class)
public class DefaultSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            MfaAuthenticationProvider mfaAuthenticationProvider,
            MfaAuthenticationDetailsSource mfaDetailsSource) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/api/register", "/login", "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // MFA enrollment + registration are JSON APIs, not browser form posts.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authenticationProvider(mfaAuthenticationProvider)
                .formLogin(form -> form
                        .loginPage("/login")
                        .authenticationDetailsSource(mfaDetailsSource)
                        .permitAll());
        return http.build();
    }

    /**
     * Password + TOTP authentication provider. Registered explicitly so this chain's
     * authentication manager uses it instead of the default password-only provider.
     */
    @Bean
    public MfaAuthenticationProvider mfaAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            TotpService totpService) {
        return new MfaAuthenticationProvider(userDetailsService, passwordEncoder, totpService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegating encoder: stores algorithm id (e.g. {bcrypt}) with each hash so
        // the algorithm can be upgraded over time without breaking existing hashes.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
