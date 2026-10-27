package com.party.registration.audit.repository;

import com.party.registration.audit.entity.ApprovalAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ApprovalAuditLogRepository extends JpaRepository<ApprovalAuditLog, Long> {

    @EntityGraph(attributePaths = {"actor", "approvalStep"})
    Page<ApprovalAuditLog> findByApplication_IdOrderByEventAtAscIdAsc(Long applicationId, Pageable pageable);

    boolean existsByEventKey(String eventKey);
}
