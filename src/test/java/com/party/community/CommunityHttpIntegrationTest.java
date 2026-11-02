package com.party.community;

import com.party.committee.entity.*;
import com.party.help.dto.HelpDtos.Create;
import com.party.help.entity.HelpCategory;
import com.party.organization.entity.*;
import com.party.security.jwt.JwtUtils;
import com.party.security.services.UserDetailsImpl;
import com.party.user.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.*;
import java.time.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CommunityHttpIntegrationTest {
    @Autowired EntityManager em;
    @Autowired JwtUtils jwt;
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    MockMvc mvc;
    ObjectMapper json=new ObjectMapper();
    AppUser citizen,outsider,member,head;
    OrganizationUnit area;
    Cookie csrf;
    String token;
    int serial;
    @BeforeEach void fixture() throws Exception {
        citizen=user(RoleType.APPLICANT);outsider=user(RoleType.APPLICANT);member=user(RoleType.APPLICANT);head=user(RoleType.PARTY_HEAD);
        var party=unit(OrganizationType.PARTY,null);var state=unit(OrganizationType.STATE,party);area=unit(OrganizationType.DIVISION,state);
        var c=Committee.builder().name("HTTP demo committee").code("HC-"+UUID.randomUUID()).committeeType(CommitteeType.DIVISIONAL_COMMITTEE).organizationUnit(area).active(true).build();em.persist(c);
        var p=CommitteePosition.builder().committee(c).positionType(CommitteePositionType.MEMBER).positionNumber(1).active(true).build();em.persist(p);
        em.persist(AdvocateAssignment.builder().committeePosition(p).advocate(member).appointedDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1)).active(true).build());em.flush();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        var result=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String cookie=result.getResponse().getHeaders("Set-Cookie").stream().filter(v->v.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        csrf=new Cookie("XSRF-TOKEN",cookie.substring(11).split(";",-1)[0]);token=json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    @Test void helpCreationRequiresSignInConsentAndCsrfAndSupportsIdempotency() throws Exception {
        String body=json.writeValueAsString(data(UUID.randomUUID(),true));
        mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON).content(body).cookie(auth(citizen))).andExpect(status().isForbidden());
        mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON).content(body).cookie(csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isUnauthorized());
        var first=mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON).content(body).cookie(auth(citizen),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isCreated()).andExpect(jsonPath("$.summary.status").value("ASSIGNED")).andExpect(jsonPath("$.requesterId").value(citizen.getId())).andExpect(header().string("Cache-Control","no-store")).andReturn();
        long id=json.readTree(first.getResponse().getContentAsString()).get("summary").get("id").asLong();
        mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON).content(body).cookie(auth(citizen),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isCreated()).andExpect(jsonPath("$.summary.id").value(id));
        mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data(UUID.randomUUID(),false))).cookie(auth(citizen),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/help-requests").param("size","1000").cookie(auth(citizen))).andExpect(status().isBadRequest());
    }
    @Test void multipartEvidenceKeepsCsrfAndCaseOwnershipAndDownloadsArePrivate() throws Exception {
        var result=create();long id=result.get("summary").get("id").asLong();long version=result.get("version").asLong();String path="/api/help-requests/"+id;
        mvc.perform(multipart(path+"/attachments").file(CommunityWorkflowIntegrationTest.image()).param("version",String.valueOf(version)).cookie(auth(citizen))).andExpect(status().isForbidden());
        var upload=mvc.perform(multipart(path+"/attachments").file(CommunityWorkflowIntegrationTest.image()).param("version",String.valueOf(version)).cookie(auth(citizen),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isOk()).andExpect(jsonPath("$.attachments.length()").value(1)).andReturn();
        long attachment=json.readTree(upload.getResponse().getContentAsString()).get("attachments").get(0).get("id").asLong();
        mvc.perform(get(path+"/attachments/"+attachment).cookie(auth(citizen))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
        mvc.perform(get(path+"/attachments/"+attachment).cookie(auth(member))).andExpect(status().isOk());
        mvc.perform(get(path+"/attachments/"+attachment).cookie(auth(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(path).cookie(auth(outsider))).andExpect(status().isNotFound());
        mvc.perform(post(path+"/comments").contentType(MediaType.APPLICATION_JSON).content("{\"version\":999,\"note\":\"Unauthorized message\"}").cookie(auth(outsider),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Test void galleryPublishingIsRestrictedAndPublicImagesWorkWithExpiredCookies() throws Exception {
        mvc.perform(get("/api/public/gallery").cookie(new Cookie("access_token","expired.invalid.token"))).andExpect(status().isOk());
        mvc.perform(multipart("/api/gallery").file(CommunityWorkflowIntegrationTest.image()).param("organizationUnitId",area.getId().toString()).param("title","Unauthorized gallery").param("description","Must not be allowed").param("eventDate","2026-10-01").param("published","true").cookie(auth(citizen),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isForbidden());
        var created=mvc.perform(multipart("/api/gallery").file(CommunityWorkflowIntegrationTest.image()).param("organizationUnitId",area.getId().toString()).param("title","Legal awareness camp").param("description","HTTP workflow photo").param("eventDate","2026-10-01").param("published","false").cookie(auth(head),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isCreated()).andReturn();
        var entry=json.readTree(created.getResponse().getContentAsString());long id=entry.get("id").asLong();
        mvc.perform(get("/api/public/gallery/"+id+"/image")).andExpect(status().isNotFound());
        mvc.perform(get("/api/gallery/"+id+"/image").cookie(auth(citizen))).andExpect(status().isForbidden());
        mvc.perform(put("/api/gallery/"+id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Legal awareness camp\",\"description\":\"Published event\",\"eventDate\":\"2026-10-01\",\"published\":true,\"version\":"+entry.get("version").asLong()+"}").cookie(auth(head),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isOk());
        mvc.perform(get("/api/public/gallery/"+id+"/image").cookie(new Cookie("access_token","expired.invalid.token"))).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/public/gallery")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Legal awareness camp")).andExpect(jsonPath("$.items[0].storageKey").doesNotExist()).andExpect(jsonPath("$.items[0].createdBy").doesNotExist());
    }
    JsonNode create() throws Exception {
        var result=mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data(UUID.randomUUID(),true))).cookie(auth(citizen),csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isCreated()).andReturn();return json.readTree(result.getResponse().getContentAsString());
    }
    Create data(UUID key,boolean consent){return new Create(key,area.getId(),"Demo Citizen","+919812345678","Demo village","Need document assistance","Please provide assistance with this demo document issue.",HelpCategory.OTHER,true,consent);}
    Cookie auth(AppUser user){return new Cookie("access_token",jwt.generateJwtTokenFromUsername(UserDetailsImpl.build(user)));}
    OrganizationUnit unit(OrganizationType type,OrganizationUnit parent){var u=OrganizationUnit.builder().name(type.toString()).code("H-"+UUID.randomUUID()).type(type).parent(parent).active(true).build();em.persist(u);return u;}
    AppUser user(RoleType type){var role=em.createQuery("select r from Role r where r.name=:name",Role.class).setParameter("name",type).getResultStream().findFirst().orElseGet(()->{var r=Role.builder().name(type).build();em.persist(r);return r;});var u=AppUser.builder().username("http-"+UUID.randomUUID()).fullName("HTTP member "+(++serial)).mobile("+9188"+String.format("%08d",serial)).password("test-only-hash").passwordChangeRequired(false).enabled(true).roles(new HashSet<>(Set.of(role))).build();em.persist(u);return u;}
}
