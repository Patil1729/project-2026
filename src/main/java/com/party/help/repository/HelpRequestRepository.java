package com.party.help.repository;
import com.party.help.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface HelpRequestRepository extends JpaRepository<HelpRequest,Long>, JpaSpecificationExecutor<HelpRequest> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select h from HelpRequest h where h.id=:id") Optional<HelpRequest> lock(@Param("id") Long id);
    Optional<HelpRequest> findByRequester_IdAndSubmissionKey(Long requesterId,String submissionKey);
    long countByRequester_IdAndStatusIn(Long id,Collection<HelpStatus> statuses);
    long countByStatusIn(Collection<HelpStatus> statuses);
    @Query("select h.assignedAppointment.advocate.id, count(h) from HelpRequest h where h.status in :statuses and h.assignedAppointment is not null group by h.assignedAppointment.advocate.id")
    List<Object[]> openLoads(@Param("statuses") Collection<HelpStatus> statuses);
}
