package com.party.administration.access;

import com.party.administration.access.dto.AreaUnitNode;
import com.party.administration.access.policy.AreaScopePolicy;
import com.party.organization.entity.OrganizationType;
import com.party.user.entity.RoleType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AreaScopePolicyTest {
    static List<AreaUnitNode> hierarchy() {
        return List.of(node(1,null,OrganizationType.PARTY,true), node(2,1L,OrganizationType.STATE,true),
                node(3,2L,OrganizationType.DIVISION,true), node(4,3L,OrganizationType.DISTRICT,true),
                node(5,4L,OrganizationType.DIVISIONAL_DISTRICT,true), node(6,5L,OrganizationType.TALUKA,true),
                node(7,2L,OrganizationType.DIVISION,true), node(8,7L,OrganizationType.DISTRICT,true),
                node(9,5L,OrganizationType.ASSEMBLY_CONSTITUENCY,false));
    }
    static AreaUnitNode node(long id,Long parent,OrganizationType type,boolean active) { return new AreaUnitNode(id,parent,type,active,"Unit "+id); }
    @Test void nineAreaRolesOnlyWithExactTypeMapping() {
        assertEquals(9,AreaScopePolicy.ROLE_TYPES.size());
        assertEquals(OrganizationType.ASSEMBLY_CONSTITUENCY,AreaScopePolicy.ROLE_TYPES.get(RoleType.CONSTITUENCY_ADMIN));
        for (var role:List.of(RoleType.APPLICANT,RoleType.PARTY_HEAD,RoleType.ADVOCATE,RoleType.FIELD_WORKER,RoleType.WARD_ADMIN))
            assertFalse(AreaScopePolicy.ROLE_TYPES.containsKey(role));
    }
    @Test void noGrantNeverBecomesGlobal() { assertEquals(List.of(),AreaScopePolicy.allowedIds(List.of(),hierarchy())); }
    @Test void districtIncludesItsDescendantsAndInactiveHistoryButNeverSibling() {
        assertEquals(List.of(4L,5L,6L,9L),AreaScopePolicy.allowedIds(List.of(new AreaScopePolicy.Grant(RoleType.DISTRICT_ADMIN,4L)),hierarchy()));
    }
    @Test void overlappingScopesAreAUnionWithoutDuplicates() {
        assertEquals(List.of(3L,4L,5L,6L,8L,9L),AreaScopePolicy.allowedIds(List.of(
                new AreaScopePolicy.Grant(RoleType.DIVISION_ADMIN,3L),new AreaScopePolicy.Grant(RoleType.TALUKA_ADMIN,6L),
                new AreaScopePolicy.Grant(RoleType.DISTRICT_ADMIN,8L)),hierarchy()));
    }
    @Test void mismatchedRoleAndMissingUnitFailClosed() {
        assertFalse(AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(RoleType.STATE_ADMIN,4L),hierarchy()));
        assertFalse(AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(RoleType.DISTRICT_ADMIN,404L),hierarchy()));
        assertFalse(AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(RoleType.WARD_ADMIN,4L),hierarchy()));
    }
    @Test void inactiveRootOrAncestorDisablesGrant() {
        assertFalse(AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(RoleType.CONSTITUENCY_ADMIN,9L),hierarchy()));
        var nodes=new ArrayList<>(hierarchy());nodes.set(2,node(3,2L,OrganizationType.DIVISION,false));
        assertFalse(AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(RoleType.DISTRICT_ADMIN,4L),nodes));
    }
    @Test void corruptDescendantTypeCannotWidenGrantedArea() {
        var nodes = new ArrayList<>(hierarchy()); nodes.add(node(99,4L,OrganizationType.STATE,true));
        assertThrows(IllegalArgumentException.class,()->AreaScopePolicy.allowedIds(List.of(new AreaScopePolicy.Grant(RoleType.DISTRICT_ADMIN,4L)),nodes));
    }
    @Test void brokenAncestryAndCyclesDoNotGrantAccess() {
        assertFalse(AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(RoleType.DISTRICT_ADMIN,4L),List.of(node(4,99L,OrganizationType.DISTRICT,true))));
        assertThrows(IllegalArgumentException.class,()->AreaScopePolicy.descendants(1L,List.of(node(1,2L,OrganizationType.PARTY,true),node(2,1L,OrganizationType.STATE,true))));
    }
}
