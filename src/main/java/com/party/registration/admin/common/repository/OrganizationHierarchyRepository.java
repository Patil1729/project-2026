package com.party.registration.admin.common.repository;

import com.party.organization.entity.OrganizationUnit;
import com.party.registration.admin.common.dto.OrganizationLink;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import java.util.List;

public interface OrganizationHierarchyRepository extends Repository<OrganizationUnit, Long> {
    @Query("select new com.party.registration.admin.common.dto.OrganizationLink(u.id, p.id) "
            + "from OrganizationUnit u left join u.parent p")
    List<OrganizationLink> findHierarchy();
    boolean existsById(Long id);
}
