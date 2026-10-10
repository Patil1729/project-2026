package com.party.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.common.entity.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/** Residential address fields for the application's India registration flow. */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 50)
    @Column(name = "house_or_flat_number", length = 50)
    private String houseOrFlatNumber;

    @Size(max = 150)
    @Column(name = "building_name", length = 150)
    private String buildingName;

    @Size(max = 200)
    @Column(length = 200)
    private String street;

    @Size(max = 150)
    @Column(length = 150)
    private String area;

    @Size(max = 200)
    @Column(length = 200)
    private String landmark;

    @Size(max = 150)
    @Column(length = 150)
    private String village;

    @Size(max = 150)
    @Column(length = 150)
    private String city;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String taluka;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String district;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String state;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String country = "India";

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{5}", message = "PIN code must contain six digits")
    @Column(name = "pin_code", nullable = false, length = 6)
    private String pinCode;

    @JsonIgnore
    @AssertTrue(message = "Provide a city or village")
    public boolean isSettlementProvided() {
        return (city != null && !city.isBlank()) || (village != null && !village.isBlank());
    }
}
