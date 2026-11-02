package com.party.community.access;

import com.party.committee.entity.AdvocateAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface CommunityAppointmentRepository extends Repository<AdvocateAssignment, Long> {
    @Query("""
        select a from AdvocateAssignment a join fetch a.advocate u
        join fetch a.committeePosition p join fetch p.committee c
        join fetch c.organizationUnit o left join fetch p.responsibilityUnit
        where a.active = true and a.endDate is null and a.appointedDate <= :today
          and u.enabled = true and p.active = true and c.active = true and o.active = true
        order by a.id
        """)
    List<AdvocateAssignment> current(@Param("today") LocalDate today);
}
