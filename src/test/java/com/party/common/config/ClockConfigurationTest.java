package com.party.common.config;

import com.party.committee.appointment.audit.repository.AppointmentAuditLogRepository;
import com.party.committee.appointment.repository.AppointmentRepository;
import com.party.committee.appointment.service.AppointmentServiceImpl;
import com.party.committee.repository.*;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.admin.common.service.AdminOrganizationScopeService;
import com.party.registration.admin.dashboard.repository.AdminDashboardRepository;
import com.party.registration.admin.dashboard.service.AdminDashboardServiceImpl;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.approval.reassignment.service.ReviewerReassignmentServiceImpl;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.registration.escalation.config.ApprovalEscalationConfiguration;
import com.party.user.repository.RoleRepository;
import com.party.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Clock;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Exercises both configurations and the three actual new Clock consumers without a database. */
class ClockConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AppConfiguration.class, ApprovalEscalationConfiguration.class,
                    AppointmentServiceImpl.class, AdminDashboardServiceImpl.class, ReviewerReassignmentServiceImpl.class)
            .withBean(AppointmentRepository.class, () -> mock(AppointmentRepository.class))
            .withBean(AppointmentAuditLogRepository.class, () -> mock(AppointmentAuditLogRepository.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(RoleRepository.class, () -> mock(RoleRepository.class))
            .withBean(CommitteeRepository.class, () -> mock(CommitteeRepository.class))
            .withBean(CommitteePositionRepository.class, () -> mock(CommitteePositionRepository.class))
            .withBean(OrganizationUnitRepository.class, () -> mock(OrganizationUnitRepository.class))
            .withBean(AdminDashboardRepository.class, () -> mock(AdminDashboardRepository.class))
            .withBean(AdminOrganizationScopeService.class, () -> mock(AdminOrganizationScopeService.class))
            .withBean(RegistrationApplicationRepository.class, () -> mock(RegistrationApplicationRepository.class))
            .withBean(RegistrationApplicantRepository.class, () -> mock(RegistrationApplicantRepository.class))
            .withBean(ApprovalStepRepository.class, () -> mock(ApprovalStepRepository.class))
            .withBean(ApprovalAuditLogRepository.class, () -> mock(ApprovalAuditLogRepository.class))
            .withBean(AdvocateAssignmentRepository.class, () -> mock(AdvocateAssignmentRepository.class));

    @Test void oneUtcClockWiresAllThreeAdministrationServices() {
        assertClockWiring(true);
    }

    @Test void clockRemainsAvailableWhenEscalationSchedulerIsDisabled() {
        assertClockWiring(false);
    }

    private void assertClockWiring(boolean enabled) {
        context.withPropertyValues("app.registration.approval.escalation.enabled=" + enabled).run(c -> {
            assertNull(c.getStartupFailure());
            assertEquals(1, c.getBeansOfType(Clock.class).size());
            Clock clock = c.getBean(Clock.class);
            assertSame(clock, c.getBean(ApprovalEscalationConfiguration.CLOCK_BEAN, Clock.class));
            assertEquals(ZoneOffset.UTC, clock.getZone());
            assertNotNull(c.getBean(AppointmentServiceImpl.class));
            assertNotNull(c.getBean(AdminDashboardServiceImpl.class));
            assertNotNull(c.getBean(ReviewerReassignmentServiceImpl.class));
        });
    }
}
