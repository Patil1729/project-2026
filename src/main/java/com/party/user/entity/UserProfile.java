package com.party.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.party.common.entity.AuditableEntity;
import com.party.electoral.entity.AssemblyConstituency;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

@Entity
@Table(name = "user_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_profile_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_profile_address", columnNames = "address_id")
        },
        indexes = @Index(name = "idx_profile_assembly", columnList = "assembly_constituency_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_profile_user"))
    private AppUser user;

    @NotNull
    @Past
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String education;

    @NotNull
    @Valid
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_profile_address"))
    private Address address;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assembly_constituency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_profile_assembly"))
    private AssemblyConstituency assemblyConstituency;

    @NotBlank
    @Size(max = 30)
    @Column(name = "voter_id_number", nullable = false, length = 30)
    private String voterIdNumber;

    // This part number is scoped to the selected constituency; not globally unique.
    @NotNull
    @Min(1)
    @Column(name = "voter_list_part_number", nullable = false)
    private Integer voterListPartNumber;

    @Pattern(regexp = "[0-9]{4}", message = "Aadhaar display reference must have four digits")
    @Column(name = "aadhaar_last_four", length = 4)
    private String aadhaarLastFour;

    // Optional encrypted payload only. Encryption/key management belongs to the service.
    // Do not bind form input or put a plaintext Aadhaar number in this field.
    @JsonIgnore
    @Size(max = 1024)
    @Column(name = "aadhaar_encrypted", length = 1024)
    private String aadhaarEncrypted;

    // Age is read-only, derived from DOB; it is never a stored, stale integer.
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer getAge() {
        return dateOfBirth == null ? null
                : Period.between(dateOfBirth, LocalDate.now(ZoneId.of("Asia/Kolkata"))).getYears();
    }
}
