package com.aegis.authserver.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the custom login page. A custom page (instead of Spring's auto-generated one)
 * is required because the form carries an extra optional {@code otp} field for TOTP MFA.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
