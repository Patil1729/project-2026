package com.party.portal;

import com.party.committee.entity.*;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.portal.repository.PortalCatalogRepository;
import com.party.portal.service.PortalCatalogService;
import com.party.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalCatalogServiceTest {
    private final PortalCatalogRepository repository = mock(PortalCatalogRepository.class);
    private final PortalCatalogService service = new PortalCatalogService(repository);
    private final OrganizationUnit party = OrganizationUnit.builder().id(1L).name("Test Party").type(OrganizationType.PARTY).active(true).build();
    private final OrganizationUnit state = OrganizationUnit.builder().id(2L).name("Maharashtra").type(OrganizationType.STATE).active(true).parent(party).build();
    private final Committee committee = Committee.builder().id(3L).name("Test State Committee").committeeType(CommitteeType.STATE_COMMITTEE).active(true).organizationUnit(state).build();

    @Test void signupCatalogIncludesValidAncestorsAndExcludesInactiveBranches() {
        var inactive = OrganizationUnit.builder().id(4L).name("Inactive").type(OrganizationType.DIVISION).parent(state).active(false).build();
        var child = OrganizationUnit.builder().id(5L).name("Hidden child").type(OrganizationType.DISTRICT).parent(inactive).active(true).build();
        when(repository.findActiveOrganizations()).thenReturn(List.of(party, state, child));
        var result = service.organizations();
        assertEquals(2, result.size()); assertEquals(1L, result.get(1).parentId());
        assertEquals("Maharashtra", result.get(1).name());
    }
    @Test void signupCatalogRejectsAnInvalidParentTypeAndCycles() {
        var invalid = OrganizationUnit.builder().id(6L).name("Wrong parent").type(OrganizationType.TALUKA).active(true).parent(state).build();
        var loop = OrganizationUnit.builder().id(7L).name("Loop").type(OrganizationType.DIVISION).active(true).build();
        loop.setParent(loop);
        when(repository.findActiveOrganizations()).thenReturn(List.of(invalid, loop));
        assertTrue(service.organizations().isEmpty());
    }
    @Test void rosterContainsAllElevenSeatsIncludingSevenDistinctVicePresidents() {
        var positions = new ArrayList<CommitteePosition>(); long id = 10;
        for (var entry : CommitteeType.STATE_COMMITTEE.getPositionTemplate().entrySet()) {
            for (int number = 1; number <= entry.getValue(); number++) {
                var position = CommitteePosition.builder().id(id++).committee(committee).positionType(entry.getKey()).positionNumber(number).active(true).build();
                if (entry.getKey() == CommitteePositionType.VICE_PRESIDENT) position.setResponsibilityUnit(
                        OrganizationUnit.builder().id(100L + number).name("Division " + number).type(OrganizationType.DIVISION).parent(state).active(true).build());
                positions.add(position);
            }
        }
        when(repository.findStatePositions(CommitteeType.STATE_COMMITTEE)).thenReturn(positions);
        when(repository.findCurrentStateAssignments(eq(CommitteeType.STATE_COMMITTEE), any(LocalDate.class))).thenReturn(List.of());
        var seats = service.statePositions();
        assertEquals(11, seats.size());
        assertEquals(7, seats.stream().filter(s -> s.positionType() == CommitteePositionType.VICE_PRESIDENT).count());
        assertTrue(seats.stream().allMatch(s -> "VACANT".equals(s.status()) && s.advocateName() == null));
    }
    @Test void currentMemberIsPublicButPrivateContactDetailsAreNot() {
        var position = president();
        var user = AppUser.builder().id(900L).fullName("Test President").username("private.login").email("private@example.test").enabled(true).build();
        var assignment = AdvocateAssignment.builder().id(30L).committeePosition(position).advocate(user).build();
        ready(position, List.of(assignment));
        var seat = service.statePositions().get(0);
        assertEquals("FILLED", seat.status()); assertEquals("Test President", seat.advocateName());
        assertFalse(seat.toString().contains("private"));
    }
    @Test void disabledOrAmbiguousCurrentAppointmentsAreNotMisrepresentedAsVacant() {
        var position = president();
        var assignment = AdvocateAssignment.builder().id(31L).committeePosition(position)
                .advocate(AppUser.builder().fullName("Hidden").enabled(false).build()).build();
        ready(position, List.of(assignment));
        var seat = service.statePositions().get(0);
        assertEquals("UNAVAILABLE", seat.status()); assertNull(seat.advocateName()); assertNull(seat.appointmentId());
        ready(position, List.of(assignment, assignment));
        assertEquals("UNAVAILABLE", service.statePositions().get(0).status());
    }
    @Test void inactiveStateAncestryIsExcludedFromThePublicRoster() {
        ready(president(), List.of()); party.setActive(false);
        assertTrue(service.statePositions().isEmpty());
    }
    private CommitteePosition president() {
        return CommitteePosition.builder().id(10L).committee(committee).positionType(CommitteePositionType.PRESIDENT).positionNumber(1).active(true).build();
    }
    private void ready(CommitteePosition position, List<AdvocateAssignment> assignments) {
        when(repository.findStatePositions(CommitteeType.STATE_COMMITTEE)).thenReturn(List.of(position));
        when(repository.findCurrentStateAssignments(eq(CommitteeType.STATE_COMMITTEE), any(LocalDate.class))).thenReturn(assignments);
    }
}
