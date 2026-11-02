package com.party.user.picture;

import com.party.security.jwt.JwtUtils;
import com.party.security.services.UserDetailsImpl;
import com.party.user.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class ProfilePictureHttpTest {
    @Autowired EntityManager em;
    @Autowired JwtUtils jwt;
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @Test void authenticatedPictureLifecycleAndAccessRules() throws Exception {
        var role=em.createQuery("select r from Role r where r.name=:name",Role.class).setParameter("name",RoleType.APPLICANT).getResultStream().findFirst().orElseGet(()->{var r=Role.builder().name(RoleType.APPLICANT).build();em.persist(r);return r;});
        var user=AppUser.builder().username("picture-"+UUID.randomUUID()).fullName("Demo photo user").mobile("+919900012345").enabled(true).password("test-only-hash").roles(new HashSet<>(Set.of(role))).build();em.persist(user);em.flush();
        var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();var auth=new Cookie("access_token",jwt.generateJwtTokenFromUsername(UserDetailsImpl.build(user)));
        var csrf=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var cookie=csrf.getResponse().getHeaders("Set-Cookie").stream().filter(v->v.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        var xsrf=new Cookie("XSRF-TOKEN",cookie.substring(11).split(";",-1)[0]);var token=new ObjectMapper().readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        String path="/api/users/"+user.getId()+"/profile-picture";
        mvc.perform(get(path).cookie(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.hasProfilePicture").value(false));
        mvc.perform(multipart(path).file(ProfilePictureTest.image("png",800,600)).cookie(auth)).andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(ProfilePictureTest.image("png",800,600)).cookie(auth,xsrf).header("X-XSRF-TOKEN",token)).andExpect(status().isOk()).andExpect(jsonPath("$.width").value(512));
        mvc.perform(get(path+"/content").cookie(auth)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/png")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(path+"/content")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/"+user.getId()).cookie(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.hasProfilePicture").value(true)).andExpect(jsonPath("$.profilePictureKey").doesNotExist());
        mvc.perform(delete(path).cookie(auth,xsrf).header("X-XSRF-TOKEN",token)).andExpect(status().isNoContent());
        mvc.perform(get(path+"/content").cookie(auth)).andExpect(status().isNotFound());
    }
}
