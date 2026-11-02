package com.party.registration.audit.service;

import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import com.party.administration.access.service.AreaAccessService;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.organization.entity.OrganizationUnit;
import org.springframework.data.domain.Page;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApplicationAuditServiceImplTest {
    final ApprovalAuditLogRepository audits = mock(ApprovalAuditLogRepository.class);
    final RegistrationApplicationRepository applications = mock(RegistrationApplicationRepository.class);
    final RegistrationApplicantRepository users = mock(RegistrationApplicantRepository.class);
    final AreaAccessService access = mock(AreaAccessService.class);
    final ApplicationAuditServiceImpl service = new ApplicationAuditServiceImpl(audits, applications, users, access);

    @Test void anotherApplicantsHistoryIsNotExposed() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(AppUser.builder().id(1L).enabled(true).build()));
        when(applications.findByIdAndApplicant_Id(9L, 1L)).thenReturn(Optional.empty());
        assertEquals("APPLICATION_NOT_FOUND", assertThrows(RegistrationException.class,
                () -> service.getMyHistory("alice", 9L, 0, 20)).getErrorCode());
        verifyNoInteractions(audits);
    }
    @Test void missingApplicationIsNotReportedAsEmptyHistory() {
        assertThrows(RegistrationException.class, () -> service.getAdminHistory(99L, 0, 20));
        verifyNoInteractions(audits);
    }
    @Test void anExistingApplicationCanHaveAnEmptyHistory() {
        when(applications.findById(9L)).thenReturn(Optional.of(RegistrationApplication.builder().id(9L).selectedShakha(OrganizationUnit.builder().id(2L).build()).build()));
        when(audits.findByApplication_IdOrderByEventAtAscIdAsc(eq(9L), any())).thenReturn(Page.empty(org.springframework.data.domain.PageRequest.of(0, 20)));
        assertEquals(0, service.getAdminHistory(9L, 0, 20).totalElements());
    }
}
