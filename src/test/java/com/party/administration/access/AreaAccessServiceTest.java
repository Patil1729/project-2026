package com.party.administration.access;

import com.party.administration.access.repository.AreaAccessRepository;
import com.party.administration.access.service.AreaAccessService;
import com.party.administration.roleassignment.entity.AreaRoleAssignment;
import com.party.administration.roleassignment.repository.AreaRoleAssignmentRepository;
import com.party.organization.dto.OrganizationUnitRequest;
import com.party.organization.entity.*;
import com.party.registration.common.exception.RegistrationException;
import com.party.security.services.UserDetailsImpl;
import com.party.user.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AreaAccessServiceTest {
    final AreaAccessRepository repository=mock(AreaAccessRepository.class);
    final AreaRoleAssignmentRepository assignments=mock(AreaRoleAssignmentRepository.class);
    final AreaAccessService service=new AreaAccessService(repository,assignments);
    @BeforeEach void setup(){when(repository.hierarchy()).thenReturn(AreaScopePolicyTest.hierarchy());signIn("DISTRICT_ADMIN");}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void signIn(String role){var principal=new UserDetailsImpl(true,10L,"district",null,"hash",List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
    void grant(){when(assignments.findByUser_IdAndActiveTrue(10L)).thenReturn(List.of(AreaRoleAssignment.builder().id(20L)
        .user(AppUser.builder().id(10L).enabled(true).build()).role(Role.builder().id(3L).name(RoleType.DISTRICT_ADMIN).build())
        .organizationUnit(OrganizationUnit.builder().id(4L).type(OrganizationType.DISTRICT).name("Kolhapur").build()).active(true).build()));}
    @Test void flatRoleWithoutAssignmentCannotManageAnything(){assertFalse(service.currentAccess().canManageAreas());assertThrows(RegistrationException.class,service::requireAdministrator);}
    @Test void scopedGrantAllowsBranchAndDeniesSibling(){grant();assertTrue(service.canAccessUnit(6L));assertFalse(service.canAccessUnit(8L));assertThrows(RegistrationException.class,()->service.requireUnit(8L));assertFalse(service.currentAccess().canManageUserAccounts());}
    @Test void partyHeadIsGlobalWithoutGrant(){signIn("PARTY_HEAD");assertTrue(service.currentAccess().globalAdministrator());assertDoesNotThrow(()->service.requireUnit(999L));verifyNoInteractions(assignments);}
    @Test void revokedGrantTakesEffectOnNextRequest(){grant();assertTrue(service.canAccessUnit(6L));when(assignments.findByUser_IdAndActiveTrue(10L)).thenReturn(List.of());assertFalse(service.canAccessUnit(6L));}
    @Test void accountReadUsesHomeAreaAndSelfRemainsReadable(){grant();when(repository.homeUnit(11L)).thenReturn(Optional.of(6L));when(repository.homeUnit(12L)).thenReturn(Optional.of(8L));assertTrue(service.canReadUser(10L));assertTrue(service.canReadUser(11L));assertFalse(service.canReadUser(12L));}
    @Test void assignedRootCannotBeMovedOrDeactivated(){grant();var request=new OrganizationUnitRequest();request.setName("Renamed");request.setCode("NEW");request.setType(OrganizationType.DISTRICT);request.setParentId(3L);
        assertDoesNotThrow(()->service.requireUpdateOrganization(4L,request));request.setParentId(7L);assertThrows(RegistrationException.class,()->service.requireUpdateOrganization(4L,request));assertThrows(RegistrationException.class,()->service.requireDeactivateOrganization(4L));}
    @Test void crossAreaCreationAndCommitteeMutationAreDenied(){grant();var request=new OrganizationUnitRequest();request.setParentId(8L);assertThrows(RegistrationException.class,()->service.requireCreateOrganization(request));when(repository.committeeUnit(55L)).thenReturn(Optional.of(8L));assertThrows(RegistrationException.class,()->service.requireCommittee(55L));}
    @Test void roleAssignmentAndGlobalAccountOperationsRemainPartyHeadOnly(){grant();assertThrows(RegistrationException.class,service::requirePartyHead);}
}
