package com.party.user.repository;

import com.party.user.entity.AppUser;
import com.party.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
