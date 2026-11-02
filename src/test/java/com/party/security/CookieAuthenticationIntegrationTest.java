package com.party.security;

import com.party.security.config.*;
import com.party.security.controller.AuthenticationController;
import com.party.security.password.controller.PasswordChangeController;
import com.party.security.password.service.PasswordChangeService;
import com.party.security.jwt.*;
import com.party.security.services.*;
import com.party.user.entity.AppUser;
import com.party.user.entity.Role;
import com.party.user.entity.RoleType;
import com.party.user.service.UserService;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = CookieAuthenticationIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
    "app.security.cookie.secure=false", "app.security.cookie.same-site=Lax",
    "app.security.cors.allowed-origins=http://localhost:5173"
})
class CookieAuthenticationIntegrationTest {
    @Configuration
    @EnableWebMvc
    @Import({WebSecurityConfiguration.class, AuthenticationController.class, PasswordChangeController.class, ProbeController.class})
    static class TestConfig {
        @Bean UserDetailsServiceImpl users() { return mock(UserDetailsServiceImpl.class); }
        @Bean PasswordChangeService passwordChanges() { return mock(PasswordChangeService.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean AuthEntryPointJwt entry() { return new AuthEntryPointJwt(); }
        @Bean JwtUtils jwt() { return new JwtUtils(Base64.getEncoder().encodeToString(new byte[32]), 3600000); }
        @Bean AuthCookieService cookies() { return new AuthCookieService(false, "Lax", 3600000); }
    }
    @RestController
    static class ProbeController {
        @GetMapping("/api/public/portal/summary") String summary() { return "public summary"; }
        @GetMapping("/api/assembly-constituencies") String constituencies() { return "constituencies"; }
        @PostMapping("/api/assembly-constituencies") String createConstituency() { return "created"; }
        @GetMapping("/api/probe") String read(java.security.Principal principal) { return principal.getName(); }
        @PostMapping("/api/probe") String write() { return "ok"; }
        @GetMapping("/api/admin/probe") String admin() { return "admin"; }
    }
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @Autowired UserDetailsServiceImpl users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtils jwt;
    @Autowired PasswordChangeService passwordChanges;
    MockMvc mvc;
    UserDetailsImpl applicant;
    record Csrf(Cookie cookie, String token) { }
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        reset(users, passwordChanges);
        Role role = new Role(); role.setName(RoleType.APPLICANT);
        applicant = UserDetailsImpl.build(AppUser.builder().id(7L).username("applicant")
                .password(encoder.encode("ExamplePass123!"))
                .enabled(true).roles(new HashSet<>(Set.of(role))).build());
        when(users.loadUserByUsername("applicant")).thenReturn(applicant);
        when(users.loadUserById(7L)).thenReturn(applicant);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private Cookie cookie(MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow();
        String value = header.substring(name.length() + 1).split(";", -1)[0];
        return new Cookie(name, value);
    }
    private Csrf csrf() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("token").asText();
        return new Csrf(cookie(result, "XSRF-TOKEN"), token);
    }
    @Test void currentUserRequiresAnAuthenticationCookie() throws Exception {
        mvc.perform(get("/api/auth/user")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/user").cookie(new Cookie("access_token", "invalid")))
                .andExpect(status().isUnauthorized());
    }
    @Test void currentUserReturnsOnlySafeIdentityFields() throws Exception {
        var result = mvc.perform(get("/api/auth/user")
                .cookie(new Cookie("access_token", jwt.generateJwtTokenFromUsername(applicant))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        var body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertEquals(7L, body.get("id").asLong());
        assertEquals("applicant", body.get("username").asText());
        assertEquals("APPLICANT", body.get("roles").get(0).asText());
        assertEquals(3, body.size());
        assertFalse(body.has("password"));
        assertFalse(body.has("jwtToken"));
        assertFalse(body.has("access_token"));
    }
    @Test void publicPortalAllowsGuestsEvenWithAnOldCookie() throws Exception {
        mvc.perform(get("/api/public/portal/summary").cookie(new Cookie("access_token", "expired")))
                .andExpect(status().isOk()).andExpect(content().string("public summary"));
    }
    @Test void applicantsCanReadConstituenciesButCannotCreateThem() throws Exception {
        var auth = new Cookie("access_token", jwt.generateJwtTokenFromUsername(applicant));
        mvc.perform(get("/api/assembly-constituencies").cookie(auth)).andExpect(status().isOk());
        var csrf = csrf();
        mvc.perform(post("/api/assembly-constituencies").cookie(auth, csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/assembly-constituencies")).andExpect(status().isUnauthorized());
    }
    @Test void changePasswordRequiresAuthenticationAndCsrf() throws Exception {
        var csrf = csrf();
        String body = "{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}";
        mvc.perform(post("/api/auth/change-password").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/change-password").cookie(new Cookie("access_token", jwt.generateJwtTokenFromUsername(applicant)))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(passwordChanges);
    }
    @Test void changePasswordUsesPrincipalAndClearsBothCookies() throws Exception {
        var csrf = csrf();
        var result = mvc.perform(post("/api/auth/change-password").cookie(csrf.cookie(), new Cookie("access_token", jwt.generateJwtTokenFromUsername(applicant)))
                .header("X-XSRF-TOKEN", csrf.token()).contentType("application/json")
                .content("{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isNoContent()).andReturn();
        verify(passwordChanges).change(eq(7L), argThat(body -> body.currentPassword().equals("OldPassword123!") && body.newPassword().equals("NewPassword123!")));
        assertTrue(result.getResponse().getHeaders("Set-Cookie").stream().anyMatch(h -> h.startsWith("access_token=") && h.contains("Max-Age=0")));
        assertTrue(result.getResponse().getHeaders("Set-Cookie").stream().anyMatch(h -> h.startsWith("XSRF-TOKEN=") && h.contains("Max-Age=0")));
    }
    @Test void signinRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/signin").contentType("application/json")
                .content("{\"username\":\"applicant\",\"password\":\"ExamplePass123!\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void csrfTokenAllowsSigninAndJwtIsOnlyInCookie() throws Exception {
        var csrf = csrf();
        var result = mvc.perform(post("/api/auth/signin").cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()).contentType("application/json")
                .content("{\"username\":\"applicant\",\"password\":\"ExamplePass123!\"}"))
                .andExpect(status().isOk()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("jwtToken"));
        String auth = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("access_token=")).findFirst().orElseThrow();
        assertTrue(auth.contains("HttpOnly")); assertTrue(auth.contains("Path=/api"));
        assertTrue(auth.contains("SameSite=Lax"));
        assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(h -> h.startsWith("XSRF-TOKEN=") && h.contains("Max-Age=0")));
        mvc.perform(get("/api/auth/user").cookie(cookie(result, "access_token")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/probe").cookie(cookie(result, "access_token")))
                .andExpect(status().isOk()).andExpect(content().string("applicant"));
    }
    @Test void cookieAuthenticationStillRequiresCsrfOnWrites() throws Exception {
        Cookie auth = new Cookie("access_token", jwt.generateJwtTokenFromUsername(applicant));
        mvc.perform(post("/api/probe").cookie(auth)).andExpect(status().isForbidden());
        var csrf = csrf();
        mvc.perform(post("/api/probe").cookie(auth, csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/probe").cookie(auth, csrf.cookie()).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
    }
    @Test void bearerHeaderAloneDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/api/probe").header("Authorization", "Bearer " + jwt.generateJwtTokenFromUsername(applicant)))
                .andExpect(status().isUnauthorized());
    }
    @Test void applicantCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/admin/probe").cookie(new Cookie("access_token", jwt.generateJwtTokenFromUsername(applicant))))
                .andExpect(status().isForbidden());
    }
    @Test void signoutClearsBothCookiesEvenWithExpiredOrInvalidJwt() throws Exception {
        mvc.perform(post("/api/auth/signout")).andExpect(status().isForbidden());
        var csrf = csrf();
        var result = mvc.perform(post("/api/auth/signout").cookie(csrf.cookie(), new Cookie("access_token", "expired"))
                .header("X-XSRF-TOKEN", csrf.token())).andExpect(status().isNoContent()).andReturn();
        for (String name : List.of("access_token", "XSRF-TOKEN"))
            assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
                    .anyMatch(h -> h.startsWith(name + "=") && h.contains("Max-Age=0") && h.contains("Path=/api")));
        mvc.perform(get("/api/probe")).andExpect(status().isUnauthorized());
    }
    @Test void corsPreflightAcceptsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/probe").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/probe").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
    @Test void secureCookieConfigurationAndInvalidCrossSiteConfiguration() {
        String cookie = new AuthCookieService(true, "Lax", 3600000).issue("test").toString();
        assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("HttpOnly"));
        assertThrows(IllegalArgumentException.class, () -> new AuthCookieService(false, "None", 3600000));
    }
}
