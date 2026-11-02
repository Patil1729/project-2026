package com.party.administration.roleassignment;

import com.party.administration.access.dto.AreaUnitNode;
import com.party.administration.access.repository.AreaAccessRepository;
import com.party.administration.access.service.AreaAccessService;
import com.party.administration.roleassignment.audit.entity.AreaRoleAuditLog;
import com.party.administration.roleassignment.audit.repository.AreaRoleAuditRepository;
import com.party.administration.roleassignment.dto.*;
import com.party.administration.roleassignment.entity.AreaRoleAssignment;
import com.party.administration.roleassignment.repository.AreaRoleAssignmentRepository;
import com.party.administration.roleassignment.service.AreaRoleAssignmentService;
import com.party.organization.entity.*;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.entity.*;
import com.party.user.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AreaRoleAssignmentServiceTest {
    final AreaRoleAssignmentRepository assignments=mock(AreaRoleAssignmentRepository.class);
    final AreaRoleAuditRepository audits=mock(AreaRoleAuditRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final RoleRepository roles=mock(RoleRepository.class);
    final OrganizationUnitRepository organizations=mock(OrganizationUnitRepository.class);
    final AreaAccessRepository hierarchy=mock(AreaAccessRepository.class);
    final AreaAccessService access=mock(AreaAccessService.class);
    final AreaRoleAssignmentService service=new AreaRoleAssignmentService(assignments,audits,users,roles,organizations,hierarchy,access);
    final Role role=Role.builder().id(3L).name(RoleType.DISTRICT_ADMIN).build();
    final AppUser target=AppUser.builder().id(10L).username("kolhapur.admin").enabled(true).roles(new HashSet<>()).build();
    final AppUser head=AppUser.builder().id(1L).username("party.head").enabled(true).build();
    final OrganizationUnit district=OrganizationUnit.builder().id(4L).name("Kolhapur").type(OrganizationType.DISTRICT).active(true).build();
    final AreaRoleGrantRequest request=new AreaRoleGrantRequest(10L,RoleType.DISTRICT_ADMIN,4L,"Assign Kolhapur administration");
    @BeforeEach void setup(){
        when(access.actorId()).thenReturn(1L);when(users.findById(1L)).thenReturn(Optional.of(head));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(target));when(organizations.findByIdForUpdate(4L)).thenReturn(Optional.of(district));
        when(roles.findByName(RoleType.DISTRICT_ADMIN)).thenReturn(Optional.of(role));
        when(hierarchy.hierarchy()).thenReturn(List.of(new AreaUnitNode(1L,null,OrganizationType.PARTY,true,"Party"),new AreaUnitNode(2L,1L,OrganizationType.STATE,true,"Maharashtra"),new AreaUnitNode(3L,2L,OrganizationType.DIVISION,true,"West Maharashtra"),new AreaUnitNode(4L,3L,OrganizationType.DISTRICT,true,"Kolhapur")));
        when(assignments.saveAndFlush(any())).thenAnswer(invocation->{AreaRoleAssignment a=invocation.getArgument(0);if(a.getId()==null)a.setId(20L);return a;});
    }
    @Test void grantAddsRoleAndAuditedScope(){var result=service.grant(request);assertTrue(result.active());assertTrue(result.effective());assertEquals(4L,result.organizationUnitId());assertTrue(target.getRoles().contains(role));verify(audits).saveAndFlush(argThat(a->a.getAction().equals("GRANTED")&&a.getActor()==head));}
    @Test void mismatchedRoleScopeIsRejected(){var bad=new AreaRoleGrantRequest(10L,RoleType.STATE_ADMIN,4L,"Wrong level");assertEquals("INVALID_ROLE_SCOPE",assertThrows(RegistrationException.class,()->service.grant(bad)).getErrorCode());verify(assignments,never()).saveAndFlush(any());}
    @Test void unsupportedLegacyWardIsNotAnAreaGrant(){assertEquals("UNSUPPORTED_AREA_ROLE",assertThrows(RegistrationException.class,()->service.grant(new AreaRoleGrantRequest(10L,RoleType.WARD_ADMIN,4L,"Legacy"))).getErrorCode());}
    @Test void disabledTargetCannotReceiveGrant(){target.setEnabled(false);assertEquals("ACCOUNT_DISABLED",assertThrows(RegistrationException.class,()->service.grant(request)).getErrorCode());}
    @Test void duplicateGrantIsAConflict(){when(assignments.findByUser_IdAndRole_NameAndOrganizationUnit_Id(10L,RoleType.DISTRICT_ADMIN,4L)).thenReturn(Optional.of(assignment()));assertEquals("AREA_ROLE_ALREADY_ASSIGNED",assertThrows(RegistrationException.class,()->service.grant(request)).getErrorCode());}
    @Test void nonPartyHeadCannotGrantOrRevoke(){doThrow(new RegistrationException(HttpStatus.FORBIDDEN,"AREA_ACCESS_DENIED","Denied")).when(access).requirePartyHead();assertThrows(RegistrationException.class,()->service.grant(request));assertThrows(RegistrationException.class,()->service.revoke(20L,new AreaRoleRevokeRequest("Denied")));verify(assignments,never()).saveAndFlush(any());}
    @Test void revocationRemovesRoleAndAudits(){var a=prepareRevocation();target.getRoles().add(role);var result=service.revoke(20L,new AreaRoleRevokeRequest("Responsibility transferred"));assertFalse(result.active());assertFalse(a.isActive());assertFalse(target.getRoles().contains(role));verify(audits).saveAndFlush(argThat(event->event.getAction().equals("REVOKED")&&event.getReason().equals("Responsibility transferred")));}
    @Test void remainingScopeKeepsSameRoleFlag(){prepareRevocation();target.getRoles().add(role);when(assignments.existsByUser_IdAndRole_NameAndActiveTrue(10L,RoleType.DISTRICT_ADMIN)).thenReturn(true);service.revoke(20L,new AreaRoleRevokeRequest("Remove one area"));assertTrue(target.getRoles().contains(role));}
    @Test void regrantReusesRowAndRetainsHistory(){var a=assignment();a.setActive(false);a.setRevokedBy(head);a.setRevocationReason("Old revoke");when(assignments.findByUser_IdAndRole_NameAndOrganizationUnit_Id(10L,RoleType.DISTRICT_ADMIN,4L)).thenReturn(Optional.of(a));assertEquals(20L,service.grant(request).id());assertNull(a.getRevocationReason());verify(audits,never()).deleteAll();}
    private AreaRoleAssignment prepareRevocation(){var a=assignment();when(assignments.findUserId(20L)).thenReturn(Optional.of(10L));when(assignments.findById(20L)).thenReturn(Optional.of(a));return a;}
    private AreaRoleAssignment assignment(){return AreaRoleAssignment.builder().id(20L).user(target).role(role).organizationUnit(district).active(true).assignedBy(head).reason("Original").build();}
}
