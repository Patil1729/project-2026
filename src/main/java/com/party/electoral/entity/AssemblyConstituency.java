package com.party.electoral.entity;

import com.party.common.entity.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Electoral master data. This is separate from the party reporting hierarchy.
 * Selecting the area supplies its official number from this row.
 */
@Entity
@Table(name = "assembly_constituencies",
        uniqueConstraints = @UniqueConstraint(name = "uk_assembly_state_number",
                columnNames = {"state_code", "constituency_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssemblyConstituency extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 10)
    @Column(name = "state_code", nullable = false, length = 10)
    private String stateCode;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotNull
    @Min(1)
    @Column(name = "constituency_number", nullable = false)
    private Integer constituencyNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
