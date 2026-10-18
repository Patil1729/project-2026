package com.party.registration.repository;

import com.party.registration.entity.ApprovalAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalAuditLogRepository extends JpaRepository<ApprovalAuditLog, Long> {
}
