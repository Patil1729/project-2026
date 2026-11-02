package com.party.security.controller;

import com.party.security.dto.LoginRequest;
import com.party.security.config.AuthCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import com.party.security.dto.LoginResponse;
import com.party.security.jwt.JwtUtils;
import com.party.security.services.UserDetailsImpl;
import com.party.user.dto.UserRequest;
import com.party.user.dto.UserResponse;
import com.party.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final AuthCookieService cookies;
    private final CookieCsrfTokenRepository csrfTokens;

    @GetMapping("/user")
    public ResponseEntity<LoginResponse> currentUser(Authentication authentication) {
        UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
        LoginResponse body = LoginResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(user.getAuthorities().stream().map(authority -> authority.getAuthority()).toList())
                .build();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        // Resolving the deferred token also writes its HttpOnly backing cookie.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    @PostMapping("/signout")
    public ResponseEntity<Void> signout(HttpServletRequest request, HttpServletResponse response) {
        csrfTokens.saveToken(null, request, response);
        SecurityContextHolder.clearContext();
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try{
            authentication=authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername().strip().toLowerCase(java.util.Locale.ROOT),
                            loginRequest.getPassword()
                    )
            );

        }catch (AuthenticationException e){

            Map<String,Object> map = new HashMap<>();
            map.put("message","Invalid username or password");
            map.put("status",false);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").body(map);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String jwtToken = jwtUtils.generateJwtTokenFromUsername(userDetails);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .toList();

        LoginResponse loginResponse = LoginResponse.builder()
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .roles(roles)
                .build();

        // Manual AuthenticationManager signin does not run the default CSRF login strategy.
        // Rotate the CSRF cookie explicitly; fetch /csrf again after signin.
        csrfTokens.saveToken(null, request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(jwtToken).toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(loginResponse);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRequest registerRequest) {

        UserResponse response = userService.createUser(registerRequest);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/users/{userId}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);


    }

}
