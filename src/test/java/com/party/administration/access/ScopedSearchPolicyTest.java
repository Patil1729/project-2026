package com.party.administration.access;

import com.party.administration.access.service.AreaAccessService;
import com.party.registration.admin.common.repository.OrganizationHierarchyRepository;
import com.party.registration.admin.common.service.AdminOrganizationScopeService;
import com.party.registration.common.exception.RegistrationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScopedSearchPolicyTest {
    final AreaAccessService access=mock(AreaAccessService.class);
    final OrganizationHierarchyRepository repository=mock(OrganizationHierarchyRepository.class);
    final AdminOrganizationScopeService service=new AdminOrganizationScopeService(repository,access);
    void scope(){when(access.requireAdministrator()).thenReturn(new AreaAccessService.Snapshot(10L,false,List.of(4L,5L,6L,9L),List.of(4L),List.of(),AreaScopePolicyTest.hierarchy()));}
    @Test void omittedFilterNeverReturnsGlobalScope(){scope();var result=service.resolve(null,true);assertFalse(result.isGlobal());assertEquals(List.of(4L,5L,6L,9L),result.unitIds());verifyNoInteractions(repository);}
    @Test void exactUnfilteredScopeUsesOnlyAssignedRoots(){scope();assertEquals(List.of(4L),service.resolve(null,false).unitIds());}
    @Test void explicitSubtreeIsIntersectedWithAllowedAreas(){scope();assertEquals(List.of(5L,6L,9L),service.resolve(5L,true).unitIds());verify(access).requireUnit(5L);}
    @Test void siblingFilterIsRejectedBeforeRepositorySearch(){scope();doThrow(new RegistrationException(HttpStatus.FORBIDDEN,"AREA_ACCESS_DENIED","Denied")).when(access).requireUnit(8L);assertThrows(RegistrationException.class,()->service.resolve(8L,true));verifyNoInteractions(repository);}
}
