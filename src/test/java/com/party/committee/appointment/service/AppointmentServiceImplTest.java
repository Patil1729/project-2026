package com.party.committee.appointment.service;

import com.party.committee.appointment.audit.entity.*;
import com.party.committee.appointment.audit.repository.AppointmentAuditLogRepository;
import com.party.committee.appointment.dto.*;
import com.party.committee.appointment.exception.AppointmentException;
import com.party.committee.appointment.repository.AppointmentRepository;
import com.party.committee.entity.*;
import com.party.committee.repository.*;
import com.party.organization.entity.*;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.user.entity.*;
import com.party.user.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {
    @Mock AppointmentRepository appointments;
    @Mock AppointmentAuditLogRepository audits;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock CommitteeRepository committees;
    @Mock CommitteePositionRepository positions;
    @Mock OrganizationUnitRepository organizations;
    AppointmentServiceImpl service;
    AppUser original;
    Committee committee;
    CommitteePosition position;
    AdvocateAssignment existing;
    Clock clock = Clock.fixed(Instant.parse("2026-10-24T09:00:00Z"), ZoneOffset.UTC);

    @BeforeEach void setup() {
        service = new AppointmentServiceImpl(appointments, audits, users, roles, committees, positions, organizations, clock);
        original = advocate(10L);
        var party = OrganizationUnit.builder().id(1L).name("Party").type(OrganizationType.PARTY).build();
        var state = OrganizationUnit.builder().id(2L).name("State").type(OrganizationType.STATE).parent(party).build();
        committee = Committee.builder().id(20L).name("State committee").committeeType(CommitteeType.STATE_COMMITTEE).organizationUnit(state).build();
        position = CommitteePosition.builder().id(30L).committee(committee).positionType(CommitteePositionType.PRESIDENT).positionNumber(1).build();
        existing = AdvocateAssignment.builder().id(40L).advocate(original).committeePosition(position).appointedDate(LocalDate.of(2026, 10, 1)).build();
    }

    @Test void bootstrapGrantsMembershipAndWritesHistory() {
        bootstrapLocks();
        when(positions.countActiveAssignmentsForPositionId(30L)).thenReturn(0L);
        stubRole();
        when(appointments.saveAndFlush(any())).thenAnswer(call -> { AdvocateAssignment value = call.getArgument(0); value.setId(41L); return value; });
        var response = service.bootstrapStatePresident(new BootstrapPresidentRequest(20L, 10L, " Initial appointment "));
        assertEquals(41L, response.id());
        assertEquals(LocalDate.of(2026, 10, 24), response.appointedDate());
        assertTrue(original.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ADVOCATE));
        verify(audits).saveAndFlush(argThat(event -> event.getAction() == AppointmentAuditAction.STATE_PRESIDENT_BOOTSTRAPPED
                && event.getReason().equals("Initial appointment")));
    }

    @Test void occupiedSeatCannotBeBootstrapped() {
        bootstrapLocks();
        when(positions.countActiveAssignmentsForPositionId(30L)).thenReturn(1L);
        assertEquals("POSITION_OCCUPIED", assertThrows(AppointmentException.class,
                () -> service.bootstrapStatePresident(new BootstrapPresidentRequest(20L, 10L, "Initial appointment"))).getErrorCode());
        verify(appointments, never()).saveAndFlush(any());
        verifyNoInteractions(audits, roles);
    }

    @Test void nonStatePresidentCannotUseBootstrap() {
        bootstrapLocks();
        committee.setCommitteeType(CommitteeType.DIVISIONAL_COMMITTEE);
        var division = OrganizationUnit.builder().id(3L).type(OrganizationType.DIVISION).parent(committee.getOrganizationUnit()).build();
        committee.setOrganizationUnit(division);
        when(organizations.findByIdForUpdate(3L)).thenReturn(Optional.of(division));
        var exception = assertThrows(AppointmentException.class, () -> service.bootstrapStatePresident(new BootstrapPresidentRequest(20L, 10L, "Initial appointment")));
        assertEquals("BOOTSTRAP_STATE_PRESIDENT_ONLY", exception.getErrorCode());
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test void disabledOrUnprofiledUserCannotBeAppointed() {
        original.setEnabled(false);
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(original));
        assertThrows(AppointmentException.class, () -> service.bootstrapStatePresident(new BootstrapPresidentRequest(20L, 10L, "Initial appointment")));
        original.setEnabled(true);
        original.setProfile(null);
        assertThrows(AppointmentException.class, () -> service.bootstrapStatePresident(new BootstrapPresidentRequest(20L, 10L, "Initial appointment")));
        verifyNoInteractions(appointments, audits);
    }

    @Test void replacementEndsOldSeatBeforeInsertingNewOneAndRetainsOriginalApplication() {
        existingLocks();
        var successor = advocate(11L);
        when(users.findByIdForUpdate(11L)).thenReturn(Optional.of(successor));
        stubRole();
        when(appointments.saveAndFlush(any())).thenAnswer(call -> { AdvocateAssignment value = call.getArgument(0); if (value.getId() == null) value.setId(41L); return value; });
        var source = com.party.registration.application.entity.RegistrationApplication.builder().id(99L).build();
        existing.setRegistrationApplication(source);
        var result = service.replace(40L, new ReplaceAppointmentRequest(11L, "Committee decision"));
        assertFalse(result.ended().active());
        assertEquals(99L, result.ended().registrationApplicationId());
        assertTrue(result.replacement().active());
        assertNull(result.replacement().registrationApplicationId());
        assertSame(source, existing.getRegistrationApplication());
        var ordered = inOrder(appointments);
        ordered.verify(appointments).saveAndFlush(existing);
        ordered.verify(appointments).saveAndFlush(argThat(value -> value.getAdvocate() == successor && value.isActive()));
        verify(audits).saveAndFlush(argThat(event -> event.getAction() == AppointmentAuditAction.APPOINTMENT_REPLACED
                && event.getRelatedAssignment().getId().equals(41L)));
        verify(audits).saveAndFlush(argThat(event -> event.getAction() == AppointmentAuditAction.APPOINTED_AS_REPLACEMENT));
    }

    @Test void cannotReplaceWithTheSameAdvocate() {
        when(appointments.findReference(40L)).thenReturn(Optional.of(new AppointmentReference(10L, 30L, 20L)));
        assertEquals("SAME_ADVOCATE", assertThrows(AppointmentException.class,
                () -> service.replace(40L, new ReplaceAppointmentRequest(10L, "No change"))).getErrorCode());
        verifyNoInteractions(users, audits);
    }

    @Test void endingAnEndedAppointmentIsIdempotent() {
        existingLocks();
        existing.endOn(LocalDate.of(2026, 10, 20));
        var response = service.end(40L, new EndAppointmentRequest("Already ended"));
        assertEquals(LocalDate.of(2026, 10, 20), response.endDate());
        verify(appointments, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test void endingPreservesAdvocateMembership() {
        existingLocks();
        original.getRoles().add(Role.builder().id(2L).name(RoleType.ADVOCATE).build());
        var response = service.end(40L, new EndAppointmentRequest("Resigned"));
        assertFalse(response.active());
        assertEquals(1, original.getRoles().size());
        verify(audits).saveAndFlush(argThat(event -> event.getAction() == AppointmentAuditAction.APPOINTMENT_ENDED));
    }

    @Test void auditFailurePropagatesSoTheTransactionCannotReportSuccess() {
        existingLocks();
        when(audits.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("audit failure"));
        assertThrows(DataIntegrityViolationException.class, () -> service.end(40L, new EndAppointmentRequest("Resigned")));
    }

    private AppUser advocate(Long id) {
        return AppUser.builder().id(id).username("advocate" + id).fullName("Advocate " + id)
                .enabled(true).profile(UserProfile.builder().id(id).build()).build();
    }

    private void bootstrapLocks() {
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(original));
        when(appointments.findPresidentSeatId(20L, CommitteePositionType.PRESIDENT)).thenReturn(Optional.of(30L));
        seatLocks();
    }

    private void existingLocks() {
        when(appointments.findReference(40L)).thenReturn(Optional.of(new AppointmentReference(10L, 30L, 20L)));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(original));
        when(appointments.findForUpdate(40L)).thenReturn(Optional.of(existing));
        seatLocks();
    }

    private void seatLocks() {
        when(committees.findByIdForUpdate(20L)).thenReturn(Optional.of(committee));
        var state = committee.getOrganizationUnit();
        when(organizations.findByIdForUpdate(2L)).thenReturn(Optional.of(state));
        when(organizations.findByIdForUpdate(1L)).thenReturn(Optional.of(state.getParent()));
        when(positions.findByIdForUpdate(30L)).thenReturn(Optional.of(position));
    }

    private void stubRole() {
        when(roles.findByName(RoleType.ADVOCATE)).thenReturn(Optional.of(Role.builder().id(2L).name(RoleType.ADVOCATE).build()));
    }
}
