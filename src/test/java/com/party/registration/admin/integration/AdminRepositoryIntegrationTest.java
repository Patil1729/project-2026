package com.party.registration.admin.integration;

import com.party.committee.entity.*;
import com.party.organization.entity.*;
import com.party.registration.admin.dashboard.service.AdminDashboardService;
import com.party.registration.admin.search.dto.ApplicationSearchRequest;
import com.party.registration.admin.search.service.AdminApplicationSearchService;
import com.party.registration.application.entity.*;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.user.entity.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: requires an isolated MySQL schema. Flyway applies V1-V5 before these tests. */
@EnabledIfEnvironmentVariable(named = "LEGALCELL_INTEGRATION", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${LEGALCELL_TEST_DB_URL}",
        "spring.datasource.username=${LEGALCELL_TEST_DB_USERNAME}",
        "spring.datasource.password=${LEGALCELL_TEST_DB_PASSWORD}",
        "app.registration.approval.escalation.enabled=false",
        "spring.jpa.show-sql=false"})
@Transactional
class AdminRepositoryIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired AdminApplicationSearchService search;
    @Autowired AdminDashboardService dashboard;
    OrganizationUnit state;
    Committee firstCommittee;
    CommitteePosition firstPosition;
    RegistrationApplication firstApplication, secondApplication;
    String token;
    int serial;

    @BeforeEach void fixture() {
        token = UUID.randomUUID().toString().substring(0, 8);
        var party = unit(OrganizationType.PARTY, null);
        state = unit(OrganizationType.STATE, party);
        var firstDivision = unit(OrganizationType.DIVISION, state);
        var secondDivision = unit(OrganizationType.DIVISION, state);
        firstCommittee = committee(firstDivision);
        var secondCommittee = committee(secondDivision);
        firstPosition = position(firstCommittee);
        var secondPosition = position(secondCommittee);
        firstApplication = application(user("Literal_%Name"), firstPosition);
        secondApplication = application(user("Second applicant"), secondPosition);
        entityManager.flush();
    }

    @Test void literalWildcardsAndDateBoundsWorkInActualMySqlQueries() {
        var request = new ApplicationSearchRequest();
        request.setOrganizationUnitId(state.getId());
        request.setQ("%");
        var result = search.search(request);
        assertEquals(1, result.totalElements());
        assertEquals(firstApplication.getId(), result.items().get(0).id());
        request.setQ(null);
        var today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        request.setCreatedFrom(today);
        request.setCreatedTo(today);
        assertEquals(2, search.search(request).totalElements());
        request.setIncludeDescendants(false);
        assertEquals(0, search.search(request).totalElements()); // Both applications belong to child divisions.
    }

    @Test void aggregateTotalsCountOverdueStepsEvenBeforeTheSchedulerRuns() {
        var reviewer = user("Committee head");
        var assignedAt = Instant.now().minus(Duration.ofDays(10));
        firstApplication.submitForReview(assignedAt);
        var step = ApprovalStep.builder().application(firstApplication).reviewerCommittee(firstCommittee).sequenceNumber(1).build();
        step.activate(reviewer, assignedAt);
        firstApplication.getApprovalSteps().add(step);
        entityManager.persist(step);
        entityManager.persist(AdvocateAssignment.builder().advocate(reviewer).committeePosition(firstPosition)
                .appointedDate(LocalDate.now(ZoneId.of("Asia/Kolkata"))).build());
        entityManager.flush();
        entityManager.clear();
        var result = dashboard.getDashboard(state.getId(), true);
        assertEquals(2, result.totalApplications());
        assertEquals(1, result.applicationsByStatus().get(RegistrationStatus.UNDER_REVIEW));
        assertEquals(1, result.overdueReviewSteps());
        assertEquals(2, result.activePositions());
        assertEquals(1, result.occupiedPositions());
        assertEquals(1, result.vacantPositions());
    }

    private OrganizationUnit unit(OrganizationType type, OrganizationUnit parent) {
        var value = OrganizationUnit.builder().name("Integration " + type).code("IT-" + token + "-" + ++serial)
                .type(type).parent(parent).build();
        entityManager.persist(value);
        return value;
    }

    private Committee committee(OrganizationUnit unit) {
        var value = Committee.builder().name("Integration committee").code("IT-C-" + token + "-" + ++serial)
                .committeeType(CommitteeType.DIVISIONAL_COMMITTEE).organizationUnit(unit).build();
        entityManager.persist(value);
        return value;
    }

    private CommitteePosition position(Committee committee) {
        var value = CommitteePosition.builder().committee(committee).positionType(CommitteePositionType.PRESIDENT).positionNumber(1).build();
        entityManager.persist(value);
        return value;
    }

    private AppUser user(String name) {
        long suffix = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L);
        var value = AppUser.builder().username("it-" + token + "-" + ++serial).fullName(name)
                .mobile("+919" + String.format(java.util.Locale.ROOT, "%09d", suffix)).password("integration-only-hash").enabled(true).build();
        entityManager.persist(value);
        return value;
    }

    private RegistrationApplication application(AppUser applicant, CommitteePosition position) {
        var value = RegistrationApplication.builder().applicationNumber("IT-APP-" + token + "-" + ++serial)
                .applicant(applicant).selectedShakha(position.getCommittee().getOrganizationUnit()).requestedPosition(position).build();
        entityManager.persist(value);
        return value;
    }
}
