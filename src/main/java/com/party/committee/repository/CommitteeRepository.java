package com.party.committee.repository;

import aj.org.objectweb.asm.commons.Remapper;
import com.party.committee.entity.Committee;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, Long> {


    boolean existsByCode( String code);

    boolean existsByOrganizationUnitId(Long id);

    @EntityGraph(attributePaths = "organizationUnit")
    Optional<Committee> findByOrganizationUnitId(Long organizationUnitId);

    @Override
    @EntityGraph(attributePaths = "organizationUnit")
    Optional<Committee> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "organizationUnit")
    List<Committee> findAll();

    boolean existsByCodeAndIdNot(String code, Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Committee c where c.id = :id")
    Optional<Committee> findByIdForUpdate(@Param("id") Long id);
}
