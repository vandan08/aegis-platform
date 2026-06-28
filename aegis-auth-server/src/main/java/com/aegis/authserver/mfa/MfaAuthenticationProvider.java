package com.aegis.authserver.mfa;

import com.aegis.authserver.user.AegisUserDetails;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link DaoAuthenticationProvider} that adds a TOTP second-factor check.
 *
 * <p>After the standard password verification, if the account has MFA enabled the OTP
 * code carried in {@link MfaWebAuthenticationDetails} must be a valid TOTP for the
 * user's secret. A missing or wrong code fails authentication like a bad password would,
 * so the login form simply gains an optional {@code otp} field.
 */
public class MfaAuthenticationProvider extends DaoAuthenticationProvider {

    private final TotpService totpService;

    public MfaAuthenticationProvider(UserDetailsService userDetailsService,
                                     PasswordEncoder passwordEncoder,
                                     TotpService totpService) {
        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.totpService = totpService;
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                  UsernamePasswordAuthenticationToken authentication)
            throws AuthenticationException {
        // 1) Standard password check.
        super.additionalAuthenticationChecks(userDetails, authentication);

        // 2) Second factor, only if the account has it enabled.
        if (userDetails instanceof AegisUserDetails aegisUser && aegisUser.isMfaEnabled()) {
            String secret = aegisUser.getUser().getMfaSecret();
            String code = authentication.getDetails() instanceof MfaWebAuthenticationDetails details
                    ? details.getOtpCode()
                    : null;
            if (secret == null || !totpService.verify(secret, code)) {
                throw new BadCredentialsException("Invalid or missing one-time code");
            }
        }
    }
}
