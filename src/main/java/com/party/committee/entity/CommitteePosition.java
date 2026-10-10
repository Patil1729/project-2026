package com.party.committee.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.common.entity.AuditableEntity;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "committee_positions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_committee_position_number",
                columnNames = {"committee_id", "position_type", "position_number"}),
        @UniqueConstraint(name = "uk_committee_position_scope",
                columnNames = {"committee_id", "position_type", "responsibility_unit_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitteePosition extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_position_committee"))
    private Committee committee;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "position_type", nullable = false, length = 50)
    private CommitteePositionType positionType;

    @NotNull
    @Min(1)
    @Column(name = "position_number", nullable = false)
    private Integer positionNumber;

    // The seven state VP seats each refer to a distinct division of that state.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsibility_unit_id",
            foreignKey = @ForeignKey(name = "fk_position_responsibility_unit"))
    private OrganizationUnit responsibilityUnit;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @JsonIgnore
    @OneToMany(mappedBy = "committeePosition", fetch = FetchType.LAZY)
    @OrderBy("appointedDate DESC, id DESC")
    @Builder.Default
    private List<AdvocateAssignment> assignments = new ArrayList<>();

    @JsonIgnore
    @AssertTrue(message = "Position type and number must belong to the committee template")
    public boolean isTemplateValid() {
        if (committee == null || committee.getType() == null
                || positionType == null || positionNumber == null) {
            return true;
        }
        Integer count = committee.getType().getPositionTemplate().get(positionType);
        return count != null && positionNumber >= 1 && positionNumber <= count;
    }

    @JsonIgnore
    @AssertTrue(message = "Only a state vice president has a responsibility division, belonging to that state")
    public boolean isResponsibilityValid() {
        if (committee == null || committee.getType() == null || positionType == null) {
            return true;
        }
        boolean stateVicePresident = committee.getType() == CommitteeType.STATE_COMMITTEE
                && positionType == CommitteePositionType.VICE_PRESIDENT;
        if (!stateVicePresident) {
            return responsibilityUnit == null;
        }
        if (responsibilityUnit == null || responsibilityUnit.getType() != OrganizationType.DIVISION) {
            return false;
        }
        OrganizationUnit state = committee.getOrganizationUnit();
        OrganizationUnit parent = responsibilityUnit.getParent();
        return state != null && parent != null && (state == parent
                || (state.getId() != null && Objects.equals(state.getId(), parent.getId())));
    }
}
