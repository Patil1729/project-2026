package com.party.committee.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.common.entity.AuditableEntity;
import com.party.organization.entity.OrganizationUnit;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "committees", uniqueConstraints = {
        @UniqueConstraint(name = "uk_committee_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_committee_organization_unit", columnNames = "organization_unit_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Committee extends AuditableEntity {

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
    private CommitteeType type;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_unit_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_committee_organization_unit"))
    private OrganizationUnit organizationUnit;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @JsonIgnore
    @OneToMany(mappedBy = "committee", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CommitteePosition> positions = new ArrayList<>();

    @JsonIgnore
    @AssertTrue(message = "Committee type must match its organization unit")
    public boolean isOrganizationTypeValid() {
        return type == null || organizationUnit == null
                || type.getOrganizationType() == organizationUnit.getType();
    }
}
