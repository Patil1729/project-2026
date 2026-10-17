package com.party.user.repository;

import com.party.user.entity.AppUser;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {


    boolean existsByUsername(String username);

    boolean existsByMobile(String mobile);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByMobileAndIdNot(String mobile, Long id);

    @EntityGraph(attributePaths = {"roles", "organizationUnit"})
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findWithDetailsById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"roles", "organizationUnit"})
    List<AppUser> findAll(Sort sort);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);
}
