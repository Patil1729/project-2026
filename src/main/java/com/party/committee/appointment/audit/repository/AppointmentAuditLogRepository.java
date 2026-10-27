package com.party.committee.appointment.audit.repository;

import com.party.committee.appointment.audit.entity.AppointmentAuditLog;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AppointmentAuditLogRepository extends JpaRepository<AppointmentAuditLog, Long> {
    @EntityGraph(attributePaths = "relatedAssignment")
    Page<AppointmentAuditLog> findByAssignment_IdOrderByEventAtAscIdAsc(Long assignmentId, Pageable pageable);
}
