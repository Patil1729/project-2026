package com.party.administration.roleassignment.audit.repository;

import com.party.administration.roleassignment.audit.entity.AreaRoleAuditLog;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;

public interface AreaRoleAuditRepository extends JpaRepository<AreaRoleAuditLog, Long> {
    @EntityGraph(attributePaths = "actor")
    Page<AreaRoleAuditLog> findByAssignment_IdOrderByEventAtDescIdDesc(Long assignmentId, Pageable pageable);
}
