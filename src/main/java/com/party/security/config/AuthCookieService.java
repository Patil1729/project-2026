package com.party.security.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Set;

@Component
public class AuthCookieService {
    public static final String NAME = "access_token";
    private final boolean secure;
    private final String sameSite;
    private final long expiration;

    public AuthCookieService(@Value("${app.security.cookie.secure:true}") boolean secure,
            @Value("${app.security.cookie.same-site:Lax}") String sameSite,
            @Value("${spring.app.expiration-in-milliseconds}") long expiration) {

        if (!Set.of("Lax", "Strict", "None").contains(sameSite))
            throw new IllegalArgumentException("Cookie SameSite must be Lax, Strict or None");

        if ("None".equals(sameSite) && !secure)
            throw new IllegalArgumentException("SameSite=None requires Secure cookies");

        if (expiration < 1000)
            throw new IllegalArgumentException("JWT lifetime must be at least one second");

        this.secure = secure;
        this.sameSite = sameSite;
        this.expiration = expiration;
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null)
            for (Cookie cookie : cookies)
            if (NAME.equals(cookie.getName()))
                return cookie.getValue();
        return null;
    }
    public ResponseCookie issue(String token) {
        return build(token, Duration.ofMillis(expiration)); }


    public ResponseCookie clear() {
        return build("", Duration.ZERO); }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie
                .from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api")
                .maxAge(age)
                .build();
    }
}
