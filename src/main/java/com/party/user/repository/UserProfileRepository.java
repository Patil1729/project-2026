package com.party.user.repository;

import com.party.user.entity.UserProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    boolean existsByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"user", "address", "assemblyConstituency"})
    Optional<UserProfile> findByUser_Id(Long userId);
}
