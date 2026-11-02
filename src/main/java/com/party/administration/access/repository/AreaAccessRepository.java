package com.party.administration.access.repository;

import com.party.administration.access.dto.AreaUnitNode;
import com.party.organization.entity.OrganizationUnit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AreaAccessRepository extends Repository<OrganizationUnit, Long> {
    @Query("select new com.party.administration.access.dto.AreaUnitNode(u.id, p.id, u.type, u.active, u.name) from OrganizationUnit u left join u.parent p order by u.id")
    List<AreaUnitNode> hierarchy();
    @Query("select u.organizationUnit.id from AppUser u where u.id = :userId")
    Optional<Long> homeUnit(@Param("userId") Long userId);
    @Query("select c.organizationUnit.id from Committee c where c.id = :id")
    Optional<Long> committeeUnit(@Param("id") Long id);
    @Query("select p.committee.organizationUnit.id from CommitteePosition p where p.id = :id")
    Optional<Long> positionUnit(@Param("id") Long id);
}
