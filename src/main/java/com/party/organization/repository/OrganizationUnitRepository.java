package com.party.organization.repository;


import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
