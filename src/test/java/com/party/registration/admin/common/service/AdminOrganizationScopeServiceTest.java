package com.party.registration.admin.common.service;

import com.party.registration.admin.common.dto.OrganizationLink;
import com.party.registration.admin.common.repository.OrganizationHierarchyRepository;
import com.party.registration.common.exception.RegistrationException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminOrganizationScopeServiceTest {
    final OrganizationHierarchyRepository repository = mock(OrganizationHierarchyRepository.class);
    final AdminOrganizationScopeService service = new AdminOrganizationScopeService(repository);

    @Test void absentUnitMeansGlobalScope() {
        assertTrue(service.resolve(null, true).isGlobal());
        verifyNoInteractions(repository);
    }
    @Test void exactScopeExcludesDescendants() {
        when(repository.existsById(2L)).thenReturn(true);
        assertEquals(List.of(2L), service.resolve(2L, false).unitIds());
        verify(repository, never()).findHierarchy();
    }
    @Test void subtreeIncludesRootAndAllBranchesButExcludesOtherRoots() {
        when(repository.findHierarchy()).thenReturn(List.of(new OrganizationLink(1L, null), new OrganizationLink(2L, 1L),
                new OrganizationLink(3L, 2L), new OrganizationLink(4L, 3L), new OrganizationLink(5L, 1L)));
        assertEquals(List.of(2L, 3L, 4L), service.resolve(2L, true).unitIds());
    }
    @Test void cycleProducesAConflict() {
        when(repository.findHierarchy()).thenReturn(List.of(new OrganizationLink(2L, 3L), new OrganizationLink(3L, 2L)));
        assertEquals("INVALID_ORGANIZATION", assertThrows(RegistrationException.class, () -> service.resolve(2L, true)).getErrorCode());
    }
    @Test void unknownUnitDoesNotBecomeGlobalScope() {
        when(repository.findHierarchy()).thenReturn(List.of());
        assertEquals("ORGANIZATION_NOT_FOUND", assertThrows(RegistrationException.class, () -> service.resolve(99L, true)).getErrorCode());
    }
}
