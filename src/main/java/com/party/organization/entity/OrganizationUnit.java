package com.party.organization.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.committee.entity.Committee;
import com.party.common.entity.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organization_units",
        uniqueConstraints = @UniqueConstraint(name = "uk_organization_unit_code", columnNames = "code"),
        indexes = @Index(name = "idx_organization_unit_parent", columnList = "parent_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationUnit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String code;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrganizationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_organization_unit_parent"))
    private OrganizationUnit parent;

    // Boolean retains getActive(), used by the existing organization service.
    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @JsonIgnore
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrganizationUnit> children = new ArrayList<>();

    @JsonIgnore
    @OneToOne(mappedBy = "organizationUnit", fetch = FetchType.LAZY)
    private Committee committee;

    @JsonIgnore
    @AssertTrue(message = "Organization type must have its required parent, and cannot parent itself")
    public boolean isHierarchyValid() {
        if (type == null) {
            return true; // @NotNull reports this separately.
        }
        if (parent == this || (id != null && parent != null && id.equals(parent.getId()))) {
            return false;
        }
        return type == OrganizationType.PARTY ? parent == null
                : parent != null && type.canHaveParent(parent.getType());
    }
}
