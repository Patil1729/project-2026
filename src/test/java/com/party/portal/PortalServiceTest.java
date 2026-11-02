package com.party.portal;

import com.party.committee.entity.*;
import com.party.organization.entity.OrganizationUnit;
import com.party.portal.repository.PortalRepository;
import com.party.portal.service.PortalService;
import com.party.user.entity.AppUser;
import com.party.user.entity.RoleType;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalServiceTest {
    private final PortalRepository repository = mock(PortalRepository.class);
    private final com.party.help.repository.HelpRequestRepository help = mock(com.party.help.repository.HelpRequestRepository.class);
    @Test void countsAdvocateMembershipsAndKeepsMissingMetricsUnknown() {
        when(repository.countRegisteredAdvocates(RoleType.ADVOCATE)).thenReturn(27L);
        var summary = new PortalService(repository, help, "").summary();
        assertEquals(27L, summary.registeredAdvocates());
        assertEquals(0L, summary.grievancesResolved()); assertNull(summary.activeLegalClinics());
        verify(repository).countRegisteredAdvocates(RoleType.ADVOCATE);
    }
    @Test void acceptsVerifiedCountersAndRejectsInvalidConfiguration() {
        var summary = new PortalService(repository, help, "12").summary();
        assertEquals(Long.valueOf(0), summary.grievancesResolved()); assertEquals(Long.valueOf(12), summary.activeLegalClinics());
        assertThrows(IllegalArgumentException.class, () -> new PortalService(repository, help, "-1"));
        assertThrows(IllegalArgumentException.class, () -> new PortalService(repository, help, "sample"));
    }
    @Test void publicCommitteeExcludesInactiveHierarchyAndProjectsOnlyPublicFields() {
        var parent = OrganizationUnit.builder().id(1L).active(true).build();
        var unit = OrganizationUnit.builder().id(2L).active(true).parent(parent).build();
        var committee = Committee.builder().id(3L).name("Maharashtra State Committee").organizationUnit(unit).build();
        var position = CommitteePosition.builder().id(4L).positionType(CommitteePositionType.PRESIDENT).positionNumber(1).committee(committee).build();
        var assignment = AdvocateAssignment.builder().id(5L).committeePosition(position)
                .advocate(AppUser.builder().id(7L).fullName("Adv. Test Person").email("private@example.test").build()).build();
        when(repository.findStateExecutive(eq(CommitteeType.STATE_COMMITTEE), any(LocalDate.class))).thenReturn(List.of(assignment));
        var service = new PortalService(repository, help, "");
        var members = service.stateExecutive(); assertEquals(1, members.size());
        assertEquals("Adv. Test Person", members.get(0).advocateName());
        assertFalse(members.get(0).toString().contains("private@example.test"));
        parent.setActive(false); assertTrue(service.stateExecutive().isEmpty());
    }
}
