package com.aegis.authserver.mfa;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Carries the one-time TOTP code submitted on the login form (the {@code otp} field)
 * alongside the usual remote-address / session details, so the authentication provider
 * can check the second factor.
 */
public class MfaWebAuthenticationDetails extends WebAuthenticationDetails {

    private final String otpCode;

    public MfaWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.otpCode = request.getParameter("otp");
    }

    public String getOtpCode() {
        return otpCode;
    }
}
