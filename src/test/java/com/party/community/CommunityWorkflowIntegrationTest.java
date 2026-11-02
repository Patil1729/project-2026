package com.party.community;

import com.party.committee.entity.*;
import com.party.community.access.CommunityAccessService;
import com.party.gallery.dto.GalleryMetadata;
import com.party.gallery.service.GalleryService;
import com.party.help.dto.HelpDtos.*;
import com.party.help.entity.*;
import com.party.help.service.HelpRequestService;
import com.party.organization.entity.*;
import com.party.portal.service.PortalService;
import com.party.registration.common.exception.RegistrationException;
import com.party.security.services.UserDetailsImpl;
import com.party.user.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CommunityWorkflowIntegrationTest {
    @Autowired EntityManager em;
    @Autowired HelpRequestService help;
    @Autowired GalleryService gallery;
    @Autowired CommunityAccessService access;
    @Autowired PortalService portal;
    OrganizationUnit party,state,division,district,subdistrict,local,sibling;
    AppUser citizen,other,localMember,senior,president,partyHead;
    AdvocateAssignment localAppointment,seniorAppointment;
    int serial;
    @BeforeEach void setup() {
        party=unit(OrganizationType.PARTY,null);state=unit(OrganizationType.STATE,party);division=unit(OrganizationType.DIVISION,state);
        district=unit(OrganizationType.DISTRICT,division);subdistrict=unit(OrganizationType.DIVISIONAL_DISTRICT,district);
        local=unit(OrganizationType.TALUKA,subdistrict);sibling=unit(OrganizationType.ASSEMBLY_CONSTITUENCY,subdistrict);
        citizen=user(RoleType.APPLICANT);other=user(RoleType.APPLICANT);localMember=user(RoleType.APPLICANT);senior=user(RoleType.APPLICANT);president=user(RoleType.APPLICANT);partyHead=user(RoleType.PARTY_HEAD);
        localAppointment=appoint(local,localMember,CommitteePositionType.MEMBER,1,null);
        seniorAppointment=appoint(subdistrict,senior,CommitteePositionType.PRESIDENT,1,null);
        em.flush();login(citizen);
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void localAssignmentUsesAppointmentsAndBalancesCurrentWorkload() {
        var second=user(RoleType.APPLICANT);var secondAppointment=appoint(local,second,CommitteePositionType.MEMBER,2,null);em.flush();
        var one=create(local);var two=create(local);
        assertEquals(localMember.getFullName(),one.summary().advocateName());assertEquals(second.getFullName(),two.summary().advocateName());
        login(localMember);assertTrue(access.capabilities().canUseInbox());assertFalse(access.capabilities().canManageGallery());
        assertEquals(1,help.list("inbox",null,null,null,0,20).totalElements());assertTrue(help.get(one.summary().id()).actions().canResolve());
        assertNotNull(secondAppointment.getId());
    }
    @Test void nearestParentFallbackAndUnassignedRequestsAreVisibleToCoordinators() {
        var h=create(sibling);assertEquals(senior.getFullName(),h.summary().advocateName());assertEquals(subdistrict.getName(),h.summary().routingAreaName());
        localAppointment.endOn(LocalDate.now(ZoneId.of("Asia/Kolkata")));seniorAppointment.endOn(LocalDate.now(ZoneId.of("Asia/Kolkata")));em.flush();
        var unassigned=create(local);assertEquals(HelpStatus.UNASSIGNED,unassigned.summary().status());assertTrue(unassigned.summary().needsReassignment());
        login(partyHead);assertEquals(2,help.list("inbox",null,null,null,0,20).totalElements());assertTrue(help.get(unassigned.summary().id()).actions().canCoordinate());
    }
    @Test void memberCanTransferOnlyUpwardAndLosesAccessAfterTransfer() {
        var h=create(local);login(localMember);
        assertEquals(seniorAppointment.getId(),help.candidates(h.summary().id(),true).get(0).assignmentId());
        var moved=help.assign(h.summary().id(),new Transfer(h.version(),"Requires senior guidance",seniorAppointment.getId()),true);
        assertEquals(subdistrict.getName(),moved.summary().routingAreaName());
        assertStatus(HttpStatus.NOT_FOUND,()->help.get(h.summary().id()));
        login(senior);assertTrue(help.get(h.summary().id()).actions().canResolve());
        assertStatus(HttpStatus.BAD_REQUEST,()->help.assign(h.summary().id(),new Transfer(moved.version(),"Cannot send down the chain",localAppointment.getId()),true));
        var timeline=help.history(h.summary().id(),0,20).items();assertTrue(timeline.stream().anyMatch(e->e.action().equals("TRANSFERRED") && e.fromAdvocate().equals(localMember.getFullName()) && e.toAdvocate().equals(senior.getFullName())));
    }
    @Test void noSeniorKeepsExistingAssignmentAndReasonIsMandatoryAtHttpBoundary() {
        seniorAppointment.endOn(LocalDate.now(ZoneId.of("Asia/Kolkata")));em.flush();var h=create(local);login(localMember);
        assertStatus(HttpStatus.CONFLICT,()->help.assign(h.summary().id(),new Transfer(h.version(),"Need a senior committee",null),true));
        var unchanged=help.get(h.summary().id());assertEquals(h.version(),unchanged.version());assertEquals(localMember.getFullName(),unchanged.summary().advocateName());
    }
    @Test void unrelatedUsersCannotReadCasesHistoryOrAttachmentsAndRoleAloneCannotHandle() {
        var h=create(local);login(other);
        assertStatus(HttpStatus.NOT_FOUND,()->help.get(h.summary().id()));assertStatus(HttpStatus.NOT_FOUND,()->help.history(h.summary().id(),0,20));
        assertStatus(HttpStatus.NOT_FOUND,()->help.download(h.summary().id(),123L));
        assertEquals(0,help.list("mine",null,null,null,0,20).totalElements());assertStatus(HttpStatus.FORBIDDEN,()->help.list("inbox",null,null,null,0,20));
        login(citizen);assertFalse(help.get(h.summary().id()).actions().canResolve());
    }
    @Test void resolutionClosureReopenAndStaleVersionGuardUpdateTheRealImpactCount() {
        var h=create(local);login(localMember);var started=help.action(h.summary().id(),new Action(h.version(),"START","Contacting the requester"));
        assertStatus(HttpStatus.CONFLICT,()->help.action(h.summary().id(),new Action(h.version(),"RESOLVE","Outdated resolution attempt")));
        var resolved=help.action(h.summary().id(),new Action(started.version(),"RESOLVE","The requested assistance has been provided."));
        assertEquals(1L,portal.summary().grievancesResolved());assertFalse(resolved.actions().canTransfer());
        login(citizen);var closed=help.action(h.summary().id(),new Action(resolved.version(),"CLOSE","Assistance received, confirmed"));assertEquals(HelpStatus.CLOSED,closed.summary().status());
        var reopened=help.action(h.summary().id(),new Action(closed.version(),"REOPEN","The issue requires more assistance"));assertEquals(HelpStatus.ASSIGNED,reopened.summary().status());assertEquals(0L,portal.summary().grievancesResolved());
    }
    @Test void endedAppointmentCannotWorkAndCoordinatorCanReassign() {
        var h=create(local);localAppointment.endOn(LocalDate.now(ZoneId.of("Asia/Kolkata")));em.flush();login(localMember);
        assertStatus(HttpStatus.NOT_FOUND,()->help.get(h.summary().id()));login(citizen);assertTrue(help.get(h.summary().id()).summary().needsReassignment());
        login(senior);var reassigned=help.assign(h.summary().id(),new Transfer(h.version(),"Previous member has left office",seniorAppointment.getId()),false);
        assertFalse(reassigned.summary().needsReassignment());assertEquals(senior.getFullName(),reassigned.summary().advocateName());
    }
    @Test void siblingAssignmentAndSelfAssignmentAreRejected() {
        var siblingMember=user(RoleType.APPLICANT);var siblingAppointment=appoint(sibling,siblingMember,CommitteePositionType.MEMBER,1,null);em.flush();
        var h=create(local);login(partyHead);
        assertStatus(HttpStatus.BAD_REQUEST,()->help.assign(h.summary().id(),new Transfer(h.version(),"Trying another unrelated branch",siblingAppointment.getId()),false));
        login(localMember);var own=create(local);assertNotEquals(localMember.getFullName(),own.summary().advocateName());
    }
    @Test void stateVicePresidentIsLimitedToTheirResponsibilityDivision() {
        localAppointment.endOn(LocalDate.now(ZoneId.of("Asia/Kolkata")));seniorAppointment.endOn(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        var otherDivision=unit(OrganizationType.DIVISION,state);var wrongVp=user(RoleType.APPLICANT);
        appoint(state,wrongVp,CommitteePositionType.VICE_PRESIDENT,1,otherDivision);em.flush();
        assertEquals(HelpStatus.UNASSIGNED,create(local).summary().status());
        var rightVp=user(RoleType.APPLICANT);appoint(state,rightVp,CommitteePositionType.VICE_PRESIDENT,2,division);em.flush();
        assertEquals(rightVp.getFullName(),create(local).summary().advocateName());
    }
    @Test void retryingSameSubmissionReturnsTheSameCase() {
        var data=data(local,UUID.randomUUID());var first=help.create(data);var retry=help.create(data);
        assertEquals(first.summary().id(),retry.summary().id());assertEquals(1,help.list("mine",null,null,null,0,20).totalElements());
    }
    @Test void attachmentsArePrivateBoundedAndVersioned() throws Exception {
        var h=create(local);var uploaded=help.upload(h.summary().id(),h.version(),image());assertEquals(1,uploaded.attachments().size());
        var content=help.download(h.summary().id(),uploaded.attachments().get(0).id());try(var input=content.input()){assertTrue(input.readAllBytes().length>0);}assertEquals("image/png",content.contentType());
        assertStatus(HttpStatus.BAD_REQUEST,()->help.upload(h.summary().id(),uploaded.version(),new MockMultipartFile("file","x.html","text/html","<html>no</html>".getBytes())));
        var current=uploaded;for(int i=1;i<5;i++) current=help.upload(h.summary().id(),current.version(),image());
        var limit=current;assertStatus(HttpStatus.CONFLICT,()->help.upload(h.summary().id(),limit.version(),image()));
    }
    @Test void galleryDraftsPublishingAndBranchRightsAreEnforced() throws Exception {
        appoint(local,president,CommitteePositionType.PRESIDENT,1,null);em.flush();login(president);
        assertTrue(access.capabilities().canManageGallery());var entry=gallery.create(local.getId(),"Legal awareness camp","A public event photo.",LocalDate.now(ZoneId.of("Asia/Kolkata")),false,image());
        assertEquals(0,gallery.list(true,null,0,20).totalElements());assertStatus(HttpStatus.NOT_FOUND,()->gallery.content(entry.id(),true));
        assertStatus(HttpStatus.FORBIDDEN,()->gallery.create(sibling.getId(),"Wrong branch","Should not publish here",LocalDate.now(ZoneId.of("Asia/Kolkata")),true,image()));
        var published=gallery.update(entry.id(),new GalleryMetadata(entry.title(),entry.description(),entry.eventDate(),true,entry.version()));
        assertEquals(1,gallery.list(true,null,0,20).totalElements());
        try(var input=gallery.content(entry.id(),true).input()){assertTrue(input.readAllBytes().length>0);}
        assertStatus(HttpStatus.CONFLICT,()->gallery.update(entry.id(),new GalleryMetadata("Stale","Outdated metadata",entry.eventDate(),false,entry.version())));
        login(other);assertStatus(HttpStatus.FORBIDDEN,()->gallery.create(local.getId(),"Unauthorized","Unauthorized upload",LocalDate.now(ZoneId.of("Asia/Kolkata")),true,image()));
        login(president);gallery.delete(entry.id(),published.version());assertEquals(0,gallery.list(true,null,0,20).totalElements());
    }
    @Test void inactiveHierarchyAndFutureAppointmentsCannotReceiveHelp() {
        localAppointment.setAppointedDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));em.flush();assertEquals(senior.getFullName(),create(local).summary().advocateName());
        division.setActive(false);em.flush();assertStatus(HttpStatus.BAD_REQUEST,()->create(local));
    }

    Detail create(OrganizationUnit unit) {return help.create(data(unit,UUID.randomUUID()));}
    Create data(OrganizationUnit unit,UUID key) {return new Create(key,unit.getId(),"Demo requester","+919876543210","Demo village","Property documents guidance","Please help us with this demonstration property documentation issue.",HelpCategory.PROPERTY_DISPUTE,false,true);}
    void login(AppUser user) {var principal=UserDetailsImpl.build(user);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
    OrganizationUnit unit(OrganizationType type,OrganizationUnit parent) {var u=OrganizationUnit.builder().name(type+" "+(++serial)).code("U-"+UUID.randomUUID()).type(type).parent(parent).active(true).build();em.persist(u);return u;}
    AppUser user(RoleType type) {
        var role=em.createQuery("select r from Role r where r.name=:name",Role.class).setParameter("name",type).getResultStream().findFirst().orElseGet(()->{var r=Role.builder().name(type).build();em.persist(r);return r;});
        var u=AppUser.builder().username("test-"+UUID.randomUUID()).fullName("Demo person "+(++serial)).mobile("+9199"+String.format("%08d",serial)).password("test-only-hash").passwordChangeRequired(false).enabled(true).roles(new HashSet<>(Set.of(role))).build();em.persist(u);return u;
    }
    AdvocateAssignment appoint(OrganizationUnit unit,AppUser user,CommitteePositionType type,int number,OrganizationUnit responsibility) {
        var committee=em.createQuery("select c from Committee c where c.organizationUnit=:unit",Committee.class).setParameter("unit",unit).getResultStream().findFirst().orElseGet(()->{var c=Committee.builder().name("Committee "+(++serial)).code("C-"+UUID.randomUUID()).committeeType(Arrays.stream(CommitteeType.values()).filter(t->t.getOrganizationType()==unit.getType()).findFirst().orElseThrow()).organizationUnit(unit).active(true).build();em.persist(c);return c;});
        var p=CommitteePosition.builder().committee(committee).positionType(type).positionNumber(number).responsibilityUnit(responsibility).active(true).build();em.persist(p);
        var a=AdvocateAssignment.builder().committeePosition(p).advocate(user).appointedDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1)).active(true).build();em.persist(a);return a;
    }
    static MockMultipartFile image() throws IOException {var image=new BufferedImage(64,48,BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return new MockMultipartFile("file","event.png","image/png",out.toByteArray());}
    interface Work {void run() throws Exception;}
    static void assertStatus(HttpStatus status,Work work) {var ex=assertThrows(RegistrationException.class,work::run);assertEquals(status,ex.getStatus(),ex.getMessage());}
}
