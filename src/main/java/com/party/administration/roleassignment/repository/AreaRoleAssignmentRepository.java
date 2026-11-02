package com.party.administration.roleassignment.repository;

import com.party.administration.roleassignment.entity.AreaRoleAssignment;
import com.party.user.entity.RoleType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AreaRoleAssignmentRepository extends JpaRepository<AreaRoleAssignment, Long> {
    @EntityGraph(attributePaths = {"role", "organizationUnit", "user"})
    List<AreaRoleAssignment> findByUser_IdAndActiveTrue(Long userId);
    Optional<AreaRoleAssignment> findByUser_IdAndRole_NameAndOrganizationUnit_Id(Long userId, RoleType role, Long unitId);
    @Query("select a.user.id from AreaRoleAssignment a where a.id = :id")
    Optional<Long> findUserId(@Param("id") Long id);
    boolean existsByUser_IdAndRole_NameAndActiveTrue(Long userId, RoleType role);
    @EntityGraph(attributePaths = {"user", "role", "organizationUnit", "assignedBy", "revokedBy"})
    @Query("select a from AreaRoleAssignment a where (:userId is null or a.user.id = :userId)")
    Page<AreaRoleAssignment> search(@Param("userId") Long userId, Pageable pageable);
}
