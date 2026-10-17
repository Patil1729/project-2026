package com.party.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.committee.entity.AdvocateAssignment;
import com.party.common.entity.AuditableEntity;
import com.party.organization.entity.OrganizationUnit;
import com.party.registration.entity.RegistrationApplication;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_user_mobile", columnNames = "mobile")
        },
        indexes = @Index(name = "idx_user_home_unit", columnList = "organization_unit_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String username;

    // A password hash only. Encode in the registration/password service.
    @JsonIgnore
    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String password;

    @NotBlank
    @Size(max = 150)
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @NotBlank
    @Size(max = 16)
    @Pattern(regexp = "^\\+?[1-9][0-9]{7,14}$", message = "Use a normalized mobile number")
    @Column(nullable = false, length = 16)
    private String mobile; // Registration WhatsApp number; normalize before saving.

    @Email
    @Size(max = 255)
    @Column(length = 255)
    private String email;

    // Optional home/shakha. It does not grant authority over this unit.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_unit_id",
            foreignKey = @ForeignKey(name = "fk_user_home_unit"))
    private OrganizationUnit organizationUnit;


    // Authentication availability, NOT application approval.
    // Registration explicitly sets true with only APPLICANT permission.
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "password_change_required", nullable = false)
    @Builder.Default
    private boolean passwordChangeRequired = true;



    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "role_id", nullable = false),
            foreignKey = @ForeignKey(name = "fk_user_roles_user"),
            inverseForeignKey = @ForeignKey(name = "fk_user_roles_role"),
            uniqueConstraints = @UniqueConstraint(name = "uk_user_role",
                    columnNames = {"user_id", "role_id"}))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @JsonIgnore
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserProfile profile;

    @JsonIgnore
    @OneToMany(mappedBy = "applicant", fetch = FetchType.LAZY)
    @OrderBy("id DESC")
    @Builder.Default
    private List<RegistrationApplication> applications = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "advocate", fetch = FetchType.LAZY)
    @OrderBy("appointedDate DESC, id DESC")
    @Builder.Default
    private List<AdvocateAssignment> assignments = new ArrayList<>();
}
