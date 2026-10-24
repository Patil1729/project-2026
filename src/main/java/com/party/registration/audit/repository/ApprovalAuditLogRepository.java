package com.party.registration.audit.repository;

import com.party.registration.audit.entity.ApprovalAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalAuditLogRepository extends JpaRepository<ApprovalAuditLog, Long> {

    boolean existsByEventKey(String eventKey);
}
