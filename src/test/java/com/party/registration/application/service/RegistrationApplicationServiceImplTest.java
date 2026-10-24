package com.party.registration.application.service;

import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.entity.CommitteePositionType;
import com.party.committee.entity.CommitteeType;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.application.dto.RegistrationApplicationRequest;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.user.entity.AppUser;
import com.party.user.entity.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationApplicationServiceImplTest {
    @Mock RegistrationApplicationRepository applications;
    @Mock RegistrationApplicantRepository applicants;
    @Mock CommitteeRepository committees;
    @Mock OrganizationUnitRepository organizations;
    @Mock CommitteePositionRepository positions;
    @Mock ApprovalAuditLogRepository audits;

    private RegistrationApplicationServiceImpl service;
    private AppUser applicant;
    private OrganizationUnit state;
    private Committee committee;
    private CommitteePosition position;
    private RegistrationApplicationRequest request;

    @BeforeEach
    void setUp() {
        service = new RegistrationApplicationServiceImpl(applications, applicants, committees,
                organizations, positions, audits);
        applicant = AppUser.builder().id(7L).username("shubham").fullName("Shubham Patil")
                .enabled(true).profile(UserProfile.builder().id(8L).build()).build();
        OrganizationUnit party = OrganizationUnit.builder().id(1L).type(OrganizationType.PARTY).build();
        state = OrganizationUnit.builder().id(2L).name("Maharashtra")
                .type(OrganizationType.STATE).parent(party).build();
        committee = Committee.builder().id(10L).name("Maharashtra Legal Cell")
                .committeeType(CommitteeType.STATE_COMMITTEE).organizationUnit(state).build();
        position = CommitteePosition.builder().id(20L).committee(committee)
                .positionType(CommitteePositionType.MEMBER).positionNumber(1).build();
        request = new RegistrationApplicationRequest();
        request.setSelectedShakhaId(2L);
        request.setRequestedPositionId(20L);
    }

    @Test
    void createsAwaitingApplicationAndCreationAuditWithoutChangingPermissions() {
        prepareCreate();
        when(applications.save(any(RegistrationApplication.class))).thenAnswer(invocation -> {
            RegistrationApplication application = invocation.getArgument(0);
            application.setId(50L);
            return application;
        });

        var response = service.createApplication("shubham", request);

        assertEquals(50L, response.getId());
        assertEquals(RegistrationStatus.AWAITING_RECOMMENDATION, response.getStatus());
        assertNull(response.getSubmittedAt());
        assertNull(response.getDecidedAt());
        assertTrue(response.getApplicationNumber().matches("REG-[A-F0-9-]{36}"));
        assertTrue(applicant.getRoles().isEmpty());
        ArgumentCaptor<ApprovalAuditLog> event = ArgumentCaptor.forClass(ApprovalAuditLog.class);
        verify(audits).saveAndFlush(event.capture());
        assertEquals(ApprovalAuditAction.REGISTRATION_CREATED, event.getValue().getAction());
        assertSame(applicant, event.getValue().getActor());
        assertEquals(50L, event.getValue().getApplication().getId());
        assertNull(event.getValue().getApplication().getAssignment());
    }

    @Test
    void blocksDuplicateOpenApplicationForSameApplicantAndPosition() {
        prepareCreate();
        when(applications.existsByApplicant_IdAndRequestedPosition_IdAndStatusIn(eq(7L), eq(20L), anyCollection()))
                .thenReturn(true);

        assertFailure("DUPLICATE_OPEN_APPLICATION", () -> service.createApplication("shubham", request));
        verify(applications, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void blocksOccupiedPosition() {
        prepareCreate();
        when(positions.countActiveAssignmentsForPositionId(20L)).thenReturn(1L);

        assertFailure("POSITION_OCCUPIED", () -> service.createApplication("shubham", request));
        verify(applications, never()).save(any());
    }

    @Test
    void requiresExistingProfile() {
        applicant.setProfile(null);
        when(applicants.findByUsernameForUpdate("shubham")).thenReturn(Optional.of(applicant));

        assertFailure("PROFILE_REQUIRED", () -> service.createApplication("shubham", request));
        verifyNoInteractions(positions, committees, organizations, applications, audits);
    }

    @Test
    void blocksDisabledApplicant() {
        applicant.setEnabled(false);
        when(applicants.findByUsernameForUpdate("shubham")).thenReturn(Optional.of(applicant));

        RegistrationException failure = assertThrows(RegistrationException.class,
                () -> service.createApplication("shubham", request));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatus());
        verifyNoInteractions(positions, committees, applications, audits);
    }

    @Test
    void rejectsPositionFromDifferentSelectedShakha() {
        when(applicants.findByUsernameForUpdate("shubham")).thenReturn(Optional.of(applicant));
        when(positions.findCommitteeIdByPositionId(20L)).thenReturn(Optional.of(10L));
        when(committees.findByIdForUpdate(10L)).thenReturn(Optional.of(committee));
        request.setSelectedShakhaId(999L);

        assertFailure("POSITION_SHAKHA_MISMATCH", () -> service.createApplication("shubham", request));
        verifyNoInteractions(organizations, applications, audits);
    }

    @Test
    void rejectsInactivePosition() {
        prepareCreate();
        position.setActive(false);
        assertFailure("INACTIVE_POSITION", () -> service.createApplication("shubham", request));
    }

    @Test
    void rejectsInactiveAncestor() {
        prepareCreate();
        state.getParent().setActive(false);
        assertFailure("INVALID_ORGANIZATION", () -> service.createApplication("shubham", request));
    }

    @Test
    void returnsNotFoundWhenApplicationIsNotOwnedByApplicant() {
        when(applicants.findByUsername("shubham")).thenReturn(Optional.of(applicant));
        when(applications.findByIdAndApplicant_Id(50L, 7L)).thenReturn(Optional.empty());

        RegistrationException failure = assertThrows(RegistrationException.class,
                () -> service.getMyApplication("shubham", 50L));
        assertEquals(HttpStatus.NOT_FOUND, failure.getStatus());
        verify(applications, never()).findById(anyLong());
    }

    private void prepareCreate() {
        when(applicants.findByUsernameForUpdate("shubham")).thenReturn(Optional.of(applicant));
        when(positions.findCommitteeIdByPositionId(20L)).thenReturn(Optional.of(10L));
        when(committees.findByIdForUpdate(10L)).thenReturn(Optional.of(committee));
        when(organizations.findByIdForUpdate(2L)).thenReturn(Optional.of(state));
        when(positions.findResponsibilityUnitIdByPositionId(20L)).thenReturn(Optional.empty());
        when(positions.findByIdForUpdate(20L)).thenReturn(Optional.of(position));
    }

    private void assertFailure(String code, org.junit.jupiter.api.function.Executable action) {
        RegistrationException failure = assertThrows(RegistrationException.class, action);
        assertEquals(code, failure.getErrorCode());
    }
}
