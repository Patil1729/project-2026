package com.party.security.password.controller;

import com.party.security.config.AuthCookieService;
import com.party.security.password.dto.ChangePasswordRequest;
import com.party.security.password.service.PasswordChangeService;
import com.party.security.services.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/change-password")
@RequiredArgsConstructor
public class PasswordChangeController {
    private final PasswordChangeService service;
    private final AuthCookieService cookies;
    private final CookieCsrfTokenRepository csrfTokens;

    @PostMapping
    public ResponseEntity<Void> change(@AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        // The user ID comes only from the authenticated principal.
        service.change(principal.getId(), body);
        csrfTokens.saveToken(null, request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
