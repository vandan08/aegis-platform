package com.aegis.authserver.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sends the site root to the account page.
 *
 * <p>Without this, a successful sign-in lands on a Whitelabel 404: form login's default success
 * URL is {@code /}, and an anonymous hit on {@code /} is saved by Spring Security and replayed
 * after authentication — but the authorization server has no page of its own at the root. Mapping
 * {@code /} here fixes both routes at once, so it is preferred over setting {@code
 * defaultSuccessUrl}, which would only cover the first.
 *
 * <p>The redirect target requires authentication, so an anonymous visitor is bounced to
 * {@code /login} and arrives at {@code /account} once signed in.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/account";
    }
}
