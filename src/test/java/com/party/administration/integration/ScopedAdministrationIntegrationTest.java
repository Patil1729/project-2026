package com.party.administration.integration;

import com.party.administration.access.service.AreaAccessService;
import com.party.administration.roleassignment.dto.*;
import com.party.administration.roleassignment.service.AreaRoleAssignmentService;
import com.party.committee.appointment.dto.AppointmentSearchRequest;
import com.party.committee.appointment.service.AppointmentService;
import com.party.committee.entity.*;
import com.party.organization.entity.*;
import com.party.registration.admin.dashboard.service.AdminDashboardService;
import com.party.registration.admin.search.dto.ApplicationSearchRequest;
import com.party.registration.admin.search.service.AdminApplicationSearchService;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.document.inbox.service.DocumentReviewInboxService;
import com.party.security.jwt.JwtUtils;
import com.party.security.services.UserDetailsImpl;
import com.party.user.entity.*;
import com.party.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs real JPQL, role persistence, filter-chain and scope checks in an isolated H2 schema.
 * MySQL/Flyway verification remains a separate opt-in test suite. */
@SpringBootTest @ActiveProfiles("test") @Transactional
class ScopedAdministrationIntegrationTest {
    @Autowired EntityManager em;
    @Autowired AreaRoleAssignmentService grants;
    @Autowired AreaAccessService access;
    @Autowired UserService users;
    @Autowired AdminApplicationSearchService search;
    @Autowired AdminDashboardService dashboard;
    @Autowired AppointmentService appointments;
    @Autowired DocumentReviewInboxService documents;
    @Autowired JwtUtils jwt;
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    AppUser head,admin,localMember,otherMember;
    OrganizationUnit district,sibling;
    Committee localCommittee,otherCommittee;
    CommitteePosition localPosition,otherPosition;
    RegistrationApplication localApplication,otherApplication;
    long grantId;
    String token=UUID.randomUUID().toString().substring(0,8);int serial;
    @BeforeEach void fixture(){
        var party=unit(OrganizationType.PARTY,null);var state=unit(OrganizationType.STATE,party);var division=unit(OrganizationType.DIVISION,state);
        district=unit(OrganizationType.DISTRICT,division);sibling=unit(OrganizationType.DISTRICT,division);
        var headRole=role(RoleType.PARTY_HEAD);var applicant=role(RoleType.APPLICANT);role(RoleType.DISTRICT_ADMIN);
        head=user("Party head",null,headRole);admin=user("Kolhapur admin",district,applicant);
        localMember=user("Kolhapur applicant",district,applicant);otherMember=user("Other district applicant",sibling,applicant);
        localCommittee=committee(district);otherCommittee=committee(sibling);localPosition=position(localCommittee);
        localApplication=application(localMember,localPosition);otherPosition=position(otherCommittee);otherApplication=application(otherMember,otherPosition);
        em.flush();login(head);grantId=grants.grant(new AreaRoleGrantRequest(admin.getId(),RoleType.DISTRICT_ADMIN,district.getId(),"Integration test scope")).id();em.flush();login(admin);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void omittedSearchAndDashboardFiltersCannotEscapeArea(){
        assertEquals(List.of(localApplication.getId()),search.search(new ApplicationSearchRequest()).items().stream().map(a->a.id()).toList());
        assertEquals(1,dashboard.getDashboard(null,true).totalApplications());
        assertEquals(Set.of(admin.getId(),localMember.getId()),new HashSet<>(users.getAllUsers().stream().map(u->u.getId()).toList()));
        var request=new ApplicationSearchRequest();request.setOrganizationUnitId(sibling.getId());assertThrows(RegistrationException.class,()->search.search(request));
    }
    @Test void directRecordAndHistoryAccessRemainScoped(){assertEquals(localApplication.getId(),search.getById(localApplication.getId()).id());assertThrows(RegistrationException.class,()->search.getById(otherApplication.getId()));assertThrows(RegistrationException.class,()->users.getUserById(otherMember.getId()));}
    @Test void revocationTakesEffectWithoutReissuingAuthentication(){assertTrue(access.hasAdminAccess());login(head);grants.revoke(grantId,new AreaRoleRevokeRequest("End test responsibility"));em.flush();login(admin);assertFalse(access.hasAdminAccess());assertThrows(RegistrationException.class,()->search.search(new ApplicationSearchRequest()));assertEquals(2,grantsHistoryAsHead());}
    @Test void areaRoleDoesNotCreatePresidencyDocumentTasks(){assertEquals(0,documents.inbox(0,10).totalElements());em.persist(AdvocateAssignment.builder().advocate(admin).committeePosition(localPosition).appointedDate(LocalDate.of(2020,1,1)).build());em.flush();assertEquals(List.of(localApplication.getId()),documents.inbox(0,10).items().stream().map(a->a.getId()).toList());}
    @Test void appointmentSearchAndDirectIdsAreScoped(){var first=AdvocateAssignment.builder().advocate(localMember).committeePosition(localPosition).appointedDate(LocalDate.of(2020,1,1)).build();em.persist(first);var other=AdvocateAssignment.builder().advocate(otherMember).committeePosition(otherPosition).appointedDate(LocalDate.of(2020,1,1)).build();em.persist(other);em.flush();assertEquals(1,appointments.search(new AppointmentSearchRequest()).totalElements());assertThrows(RegistrationException.class,()->appointments.getById(other.getId()));}
    @Test void httpUsesFreshGrantStateAndKeepsGlobalWritesClosed() throws Exception {
        MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        var cookie=new Cookie("access_token",jwt.generateJwtTokenFromUsername(UserDetailsImpl.build(admin)));
        mvc.perform(get("/api/admin/registration-applications").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/registration-applications/"+otherApplication.getId()).cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/role-assignments").cookie(cookie)).andExpect(status().isForbidden());
        login(head);grants.revoke(grantId,new AreaRoleRevokeRequest("Remove HTTP scope"));em.flush();
        mvc.perform(get("/api/admin/registration-applications").cookie(cookie)).andExpect(status().isForbidden());
    }
    @Test void publicCatalogueWorksWithAnExpiredAuthenticationCookie() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        for(var path:List.of("/api/public/portal/organization-units","/api/public/portal/state-executive/positions"))mvc.perform(get(path).cookie(new Cookie("access_token","expired.invalid.token"))).andExpect(status().isOk());
    }
    private long grantsHistoryAsHead(){login(head);return grants.history(grantId,0,20).totalElements();}
    private void login(AppUser user){var principal=UserDetailsImpl.build(user);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
    private Role role(RoleType type){var role=Role.builder().name(type).build();em.persist(role);return role;}
    private OrganizationUnit unit(OrganizationType type,OrganizationUnit parent){var u=OrganizationUnit.builder().name("IT "+type+" "+serial).code("IT-"+token+"-"+ ++serial).type(type).parent(parent).build();em.persist(u);return u;}
    private AppUser user(String name,OrganizationUnit unit,Role role){var u=AppUser.builder().username("it-"+token+"-"+ ++serial).fullName(name).mobile("+919"+String.format(Locale.ROOT,"%09d",Math.abs((long)UUID.randomUUID().hashCode())%1000000000L)).password("integration-only-hash").organizationUnit(unit).roles(new HashSet<>(Set.of(role))).enabled(true).build();em.persist(u);return u;}
    private Committee committee(OrganizationUnit unit){var c=Committee.builder().name("IT Committee").code("IT-C-"+token+"-"+ ++serial).committeeType(CommitteeType.DISTRICT_COMMITTEE).organizationUnit(unit).build();em.persist(c);return c;}
    private CommitteePosition position(Committee committee){var p=CommitteePosition.builder().committee(committee).positionType(CommitteePositionType.PRESIDENT).positionNumber(1).build();em.persist(p);return p;}
    private RegistrationApplication application(AppUser user,CommitteePosition position){var a=RegistrationApplication.builder().applicationNumber("IT-APP-"+token+"-"+ ++serial).applicant(user).selectedShakha(position.getCommittee().getOrganizationUnit()).requestedPosition(position).build();em.persist(a);return a;}
}
