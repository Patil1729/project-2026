package com.party.organization.repository;


import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Long> {

    Optional<OrganizationUnit> findByCode(String code);

    boolean existsByCode(String code);

    List<OrganizationUnit> findByParentId(Long parentId);

    List<OrganizationUnit> findByType(OrganizationType type);

    List<OrganizationUnit> findByParentIsNull();

    boolean existsByCodeAndIdNot(String code, Long id);

    List<OrganizationUnit> findByActiveTrue();

    boolean existsByParentId(Long parentId);

    @EntityGraph(attributePaths = "parent")
    List<OrganizationUnit> findAllByActiveTrue();

    boolean existsByParentIdAndActiveTrue(Long id);

    List<OrganizationUnit> findByParentIdAndActiveTrue(Long parentId);

    List<OrganizationUnit> findByParentIsNullAndActiveTrue();

    List<OrganizationUnit> findByParent_IdAndTypeAndActiveTrueOrderByIdAsc(Long id, OrganizationType organizationType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from OrganizationUnit u where u.id = :id")
    Optional<OrganizationUnit> findByIdForUpdate(@Param("id") Long id);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
            @QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "0"
            )
    )
    @Query("""
        select u
        from OrganizationUnit u
        where u.parent.id = :stateId
          and u.type = :divisionType
          and u.active = true
        order by u.id
        """)
    List<OrganizationUnit> findActiveDivisionsForUpdate(
            @Param("stateId") Long stateId,
            @Param("divisionType") OrganizationType divisionType);
}
