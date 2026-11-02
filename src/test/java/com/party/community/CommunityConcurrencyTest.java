package com.party.community;

import com.party.help.dto.HelpDtos.Create;
import com.party.help.entity.HelpCategory;
import com.party.help.service.HelpRequestService;
import com.party.organization.entity.*;
import com.party.security.services.UserDetailsImpl;
import com.party.user.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Separate committed transactions exercise duplicate submission locking, not a mocked repository. */
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:community-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityConcurrencyTest {
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired HelpRequestService help;
    record Fixture(UserDetailsImpl actor,Long areaId) { }
    @Test void simultaneousRetriesCreateExactlyOneRequest() throws Exception {
        var fixture=new TransactionTemplate(transactions).execute(status->{
            var role=Role.builder().name(RoleType.APPLICANT).build();em.persist(role);
            var user=AppUser.builder().username("concurrent-requester").fullName("Concurrent requester").mobile("+919812345678").password("test-only-hash").passwordChangeRequired(false).enabled(true).roles(new HashSet<>(Set.of(role))).build();em.persist(user);
            var party=OrganizationUnit.builder().name("Demo party").code("CONCURRENT-PARTY").type(OrganizationType.PARTY).active(true).build();em.persist(party);
            var state=OrganizationUnit.builder().name("Demo state").code("CONCURRENT-STATE").type(OrganizationType.STATE).parent(party).active(true).build();em.persist(state);em.flush();
            return new Fixture(UserDetailsImpl.build(user),state.getId());
        });
        assertNotNull(fixture);
        var input=new Create(UUID.randomUUID(),fixture.areaId(),"Demo requester","+919812345678","Demo locality","Concurrent submission","Two concurrent retries must return a single saved request.",HelpCategory.OTHER,false,true);
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Long> submit=()->{
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(fixture.actor(),null,fixture.actor().getAuthorities()));
                try {ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return help.create(input).summary().id();}
                finally {SecurityContextHolder.clearContext();}
            };
            var first=pool.submit(submit);var second=pool.submit(submit);assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertEquals(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(fixture.actor(),null,fixture.actor().getAuthorities()));
            assertEquals(1,help.list("mine",null,null,null,0,10).totalElements());
        } finally {SecurityContextHolder.clearContext();pool.shutdownNow();}
    }
}
