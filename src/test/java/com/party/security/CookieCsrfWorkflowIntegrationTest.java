package com.party.security;

import com.party.administration.access.service.AreaAccessService;
import com.party.registration.application.controller.RegistrationApplicationController;
import com.party.registration.application.dto.RegistrationApplicationResponse;
import com.party.registration.application.service.RegistrationApplicationService;
import com.party.registration.document.controller.ApplicationDocumentController;
import com.party.registration.document.dto.ApplicationDocumentResponse;
import com.party.registration.document.entity.DocumentStatus;
import com.party.registration.document.entity.DocumentType;
import com.party.registration.document.service.ApplicationDocumentService;
import com.party.security.jwt.JwtUtils;
import com.party.security.services.UserDetailsImpl;
import com.party.security.services.UserDetailsServiceImpl;
import com.party.user.controller.UserProfileController;
import com.party.user.dto.UserProfileResponse;
import com.party.user.entity.AppUser;
import com.party.user.entity.Role;
import com.party.user.entity.RoleType;
import com.party.user.service.UserProfileService;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security filters and controllers, with a browser-style jar that applies Set-Cookie
 * after EVERY response. Services are mocked to isolate authentication/CSRF from storage. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = CookieCsrfWorkflowIntegrationTest.Config.class)
@TestPropertySource(properties = {
        "app.security.cookie.secure=false", "app.security.cookie.same-site=Lax",
        "app.security.cors.allowed-origins=http://localhost:5173"
})
class CookieCsrfWorkflowIntegrationTest {
    @Configuration
    @Import({CookieAuthenticationIntegrationTest.TestConfig.class, UserProfileController.class,
            RegistrationApplicationController.class, ApplicationDocumentController.class})
    static class Config {
        @Bean UserProfileService profileService() { return mock(UserProfileService.class); }
        @Bean RegistrationApplicationService applications() { return mock(RegistrationApplicationService.class); }
        @Bean ApplicationDocumentService documents() { return mock(ApplicationDocumentService.class); }
        @Bean(name = "areaAccess") AreaAccessService areaAccess() { return mock(AreaAccessService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @Autowired UserDetailsServiceImpl users;
    @Autowired UserProfileService profiles;
    @Autowired RegistrationApplicationService applications;
    @Autowired ApplicationDocumentService documents;
    @Autowired JwtUtils jwt;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();
    record Csrf(Cookie cookie, String token) { }
    final String profileBody = """
            {"dateOfBirth":"1990-01-01","education":"Test education","assemblyConstituencyId":1,
             "voterIdNumber":"TEST-ONLY","voterListPartNumber":1,"address":{"village":"Halakarni",
             "taluka":"Chandgad","district":"Kolhapur","state":"Maharashtra","country":"India","pinCode":"416509"}}
            """;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        reset(users, profiles, applications, documents);
        when(profiles.updateProfile(eq(10L), any())).thenReturn(UserProfileResponse.builder().id(1L).userId(10L).build());
        when(profiles.getProfileByUserId(10L)).thenReturn(UserProfileResponse.builder().id(1L).userId(10L).build());
        when(applications.createApplication(eq("test-10"), any())).thenReturn(RegistrationApplicationResponse.builder().id(25L).applicantId(10L).build());
        when(documents.upload(eq("test-10"), eq(25L), any())).thenReturn(new ApplicationDocumentResponse(
                30L, 25L, DocumentType.RECOMMENDATION_LETTER, 1, "recommendation.pdf", "application/pdf", 20L,
                "a".repeat(64), DocumentStatus.UPLOADED, 10L, Instant.parse("2026-01-01T00:00:00Z"), null, null, null));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void readsProfileSaveApplicationCreationAndMultipartUploadKeepTheCsrfPair() throws Exception {
        Cookie auth = identity(10, RoleType.APPLICANT);
        Csrf csrf = csrf();
        Map<String, Cookie> jar = jar(auth, csrf.cookie());
        // Before the fix the first authenticated GET cleared XSRF-TOKEN; the PUT then failed.
        applyCookies(mvc.perform(get("/api/auth/user").cookie(cookies(jar))).andExpect(status().isOk()).andReturn(), jar);
        applyCookies(mvc.perform(get("/api/users/10/profile").cookie(cookies(jar))).andExpect(status().isOk()).andReturn(), jar);
        assertEquals(csrf.cookie().getValue(), jar.get("XSRF-TOKEN").getValue());
        MvcResult saved = mvc.perform(put("/api/users/10/profile").cookie(cookies(jar))
                .header("X-XSRF-TOKEN", csrf.token()).contentType("application/json").content(profileBody))
                .andExpect(status().isOk()).andReturn();
        applyCookies(saved, jar);
        MvcResult created = mvc.perform(post("/api/registration-applications").cookie(cookies(jar))
                .header("X-XSRF-TOKEN", csrf.token()).contentType("application/json")
                .content("{\"selectedShakhaId\":6,\"requestedPositionId\":123}"))
                .andExpect(status().isCreated()).andReturn();
        applyCookies(created, jar);
        MockMultipartFile file = new MockMultipartFile("file", "recommendation.pdf", "application/pdf",
                "%PDF-1.4\nTest-only\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/registration-applications/25/documents").file(file)
                .param("documentType", "RECOMMENDATION_LETTER").cookie(cookies(jar)).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isCreated());
        verify(profiles).updateProfile(eq(10L), any());
        verify(applications).createApplication(eq("test-10"), any());
        verify(documents).upload(eq("test-10"), eq(25L), argThat(request -> request.getDocumentType() == DocumentType.RECOMMENDATION_LETTER));
    }

    @Test void realSigninAndSignoutStillRotateAndRequireFreshCsrf() throws Exception {
        identity(10, RoleType.APPLICANT);
        Csrf beforeLogin = csrf();
        Map<String, Cookie> jar = jar(beforeLogin.cookie());
        MvcResult signin = mvc.perform(post("/api/auth/signin").cookie(cookies(jar))
                .header("X-XSRF-TOKEN", beforeLogin.token()).contentType("application/json")
                .content("{\"username\":\"test-10\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isOk()).andReturn();
        applyCookies(signin, jar);
        assertTrue(jar.containsKey("access_token"));
        assertFalse(jar.containsKey("XSRF-TOKEN"));
        mvc.perform(put("/api/users/10/profile").cookie(cookies(jar)).header("X-XSRF-TOKEN", beforeLogin.token())
                .contentType("application/json").content(profileBody))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_MISSING"));
        Csrf afterLogin = csrf();
        jar.put(afterLogin.cookie().getName(), afterLogin.cookie());
        mvc.perform(put("/api/users/10/profile").cookie(cookies(jar)).header("X-XSRF-TOKEN", afterLogin.token())
                .contentType("application/json").content(profileBody)).andExpect(status().isOk());
        MvcResult signout = mvc.perform(post("/api/auth/signout").cookie(cookies(jar)).header("X-XSRF-TOKEN", afterLogin.token()))
                .andExpect(status().isNoContent()).andReturn();
        applyCookies(signout, jar);
        assertFalse(jar.containsKey("access_token"));
        assertFalse(jar.containsKey("XSRF-TOKEN"));
        mvc.perform(get("/api/auth/user")).andExpect(status().isUnauthorized());
    }

    @Test void ownerWithMatchingCsrfCanUpdateOwnProfile() throws Exception {
        Cookie auth = identity(10, RoleType.APPLICANT);
        Csrf token = csrf();
        mvc.perform(put("/api/users/10/profile").cookie(auth, token.cookie()).header("Origin", "http://localhost:5173")
                .header("X-XSRF-TOKEN", token.token()).contentType("application/json").content(profileBody)).andExpect(status().isOk());
    }

    @Test void anotherAreaAdministratorCannotWriteTheProfile() throws Exception {
        Cookie auth = identity(11, RoleType.STATE_ADMIN);
        Csrf token = csrf();
        mvc.perform(put("/api/users/10/profile").cookie(auth, token.cookie()).header("Origin", "http://localhost:5173")
                .header("X-XSRF-TOKEN", token.token()).contentType("application/json").content(profileBody))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(profiles, never()).updateProfile(any(), any());
    }

    @Test void partyHeadCanWriteAnotherMembersProfile() throws Exception {
        Cookie auth = identity(11, RoleType.PARTY_HEAD);
        Csrf token = csrf();
        mvc.perform(put("/api/users/10/profile").cookie(auth, token.cookie()).header("X-XSRF-TOKEN", token.token())
                .contentType("application/json").content(profileBody)).andExpect(status().isOk());
    }

    @Test void mismatchedCsrfReturnsAnExplicitCodeWithoutDisclosingTokens() throws Exception {
        Cookie auth = identity(10, RoleType.APPLICANT);
        Csrf oldToken = csrf(), replacement = csrf();
        MvcResult result = mvc.perform(put("/api/users/10/profile").cookie(auth, replacement.cookie())
                .header("X-XSRF-TOKEN", oldToken.token()).contentType("application/json").content(profileBody))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(oldToken.token()));
        assertFalse(result.getResponse().getContentAsString().contains(replacement.cookie().getValue()));
        verify(profiles, never()).updateProfile(any(), any());
    }

    @Test void missingCsrfCookieStillRejectsAWriteWithAHeader() throws Exception {
        Cookie auth = identity(10, RoleType.APPLICANT);
        Csrf token = csrf();
        mvc.perform(put("/api/users/10/profile").cookie(auth).header("X-XSRF-TOKEN", token.token())
                .contentType("application/json").content(profileBody)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_MISSING"));
        verify(profiles, never()).updateProfile(any(), any());
    }

    private Cookie identity(long id, RoleType type) {
        Role role = Role.builder().name(type).build();
        UserDetailsImpl principal = UserDetailsImpl.build(AppUser.builder().id(id).username("test-" + id)
                .password(encoder.encode("TestPassword123!")).roles(Set.of(role)).enabled(true).build());
        when(users.loadUserById(id)).thenReturn(principal);
        when(users.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        return new Cookie("access_token", jwt.generateJwtTokenFromUsername(principal));
    }
    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Map<String, Cookie> jar = new LinkedHashMap<>();
        applyCookies(result, jar);
        return new Csrf(jar.get("XSRF-TOKEN"), mapper.readTree(result.getResponse().getContentAsString()).get("token").asText());
    }
    private Map<String, Cookie> jar(Cookie... cookies) {
        Map<String, Cookie> jar = new LinkedHashMap<>();
        for (Cookie cookie : cookies) jar.put(cookie.getName(), cookie);
        return jar;
    }
    private Cookie[] cookies(Map<String, Cookie> jar) { return jar.values().toArray(Cookie[]::new); }
    private void applyCookies(MvcResult result, Map<String, Cookie> jar) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            String[] pair = header.split(";", -1)[0].split("=", 2);
            if (header.contains("Max-Age=0")) jar.remove(pair[0]);
            else jar.put(pair[0], new Cookie(pair[0], pair.length == 2 ? pair[1] : ""));
        }
    }
}
