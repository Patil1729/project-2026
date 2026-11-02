package com.party.security;

import com.party.security.jwt.*;
import com.party.security.config.AuthCookieService;
import jakarta.servlet.http.Cookie;
import com.party.security.services.*;
import com.party.user.entity.AppUser;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtSecurityTest {
    private final String secret = Base64.getEncoder().encodeToString(new byte[32]);
    private final JwtUtils jwt = new JwtUtils(secret, 3600000);
    private UserDetailsImpl user(boolean enabled) {
        return UserDetailsImpl.build(AppUser.builder().id(7L).username("applicant")
                .password("hash").enabled(enabled).build());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void tokenUsesStableIdAndRejectsAnotherSigningKey() {
        String token = jwt.generateJwtTokenFromUsername(user(true));
        assertEquals(7L, jwt.getUserIdFromJwtToken(token));
        byte[] differentKey = new byte[32];
        java.util.Arrays.fill(differentKey, (byte) 0x5a);
        var other = new JwtUtils(Base64.getEncoder().encodeToString(differentKey), 3600000);
        assertThrows(JwtException.class, () -> other.getUserIdFromJwtToken(token));
    }
    @Test void expiredTokenIsRejected() {
        String token = io.jsonwebtoken.Jwts.builder().subject("7")
                .expiration(new java.util.Date(0))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))).compact();
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> jwt.getUserIdFromJwtToken(token));
    }
    @Test void disabledAccountCannotUseExistingToken() throws Exception {
        var service = mock(UserDetailsServiceImpl.class);
        when(service.loadUserById(7L)).thenReturn(user(false));
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", jwt.generateJwtTokenFromUsername(user(true))));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new AuthTokenFilter(jwt, service, new AuthEntryPointJwt(), new AuthCookieService(false, "Lax", 3600000)).doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void validTokenLoadsCurrentUserAndContinues() throws Exception {
        var service = mock(UserDetailsServiceImpl.class);
        var user = user(true);
        when(service.loadUserById(7L)).thenReturn(user);
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", jwt.generateJwtTokenFromUsername(user)));
        var chain = new MockFilterChain();
        new AuthTokenFilter(jwt, service, new AuthEntryPointJwt(), new AuthCookieService(false, "Lax", 3600000)).doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        assertEquals("applicant", SecurityContextHolder.getContext().getAuthentication().getName());
    }
    @Test void passwordChangeInvalidatesPreviouslyIssuedCookies() throws Exception {
        var current = user(true);
        String previous = jwt.generateJwtTokenFromUsername(current);
        assertTrue(jwt.matchesCurrentCredentials(previous, current));
        current.setPassword("new-password-hash");
        assertFalse(jwt.matchesCurrentCredentials(previous, current));
        var service = mock(UserDetailsServiceImpl.class);
        when(service.loadUserById(7L)).thenReturn(current);
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", previous));
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        new AuthTokenFilter(jwt, service, new AuthEntryPointJwt(), new AuthCookieService(false, "Lax", 3600000)).doFilter(request, response, chain);
        assertEquals(401, response.getStatus()); assertNull(chain.getRequest());
        assertTrue(jwt.matchesCurrentCredentials(jwt.generateJwtTokenFromUsername(current), current));
    }
    @Test void legacyTokenWithoutCredentialStampRequiresFreshSignin() {
        String legacy = io.jsonwebtoken.Jwts.builder().subject("7")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))).compact();
        assertFalse(jwt.matchesCurrentCredentials(legacy, user(true)));
    }
    @Test void malformedTokenReturns401() throws Exception {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "broken"));
        var response = new MockHttpServletResponse();
        new AuthTokenFilter(jwt, mock(UserDetailsServiceImpl.class), new AuthEntryPointJwt(), new AuthCookieService(false, "Lax", 3600000))
                .doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }
}
