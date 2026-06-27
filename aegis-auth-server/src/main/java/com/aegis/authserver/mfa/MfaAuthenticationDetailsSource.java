package com.aegis.authserver.mfa;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;

/**
 * Produces {@link MfaWebAuthenticationDetails} so the submitted OTP is captured into the
 * authentication token's details during form login.
 */
@Component
public class MfaAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, MfaWebAuthenticationDetails> {

    @Override
    public MfaWebAuthenticationDetails buildDetails(HttpServletRequest request) {
        return new MfaWebAuthenticationDetails(request);
    }
}
