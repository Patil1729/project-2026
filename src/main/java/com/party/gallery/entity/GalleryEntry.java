package com.party.gallery.entity;
import com.party.common.entity.AuditableEntity;
import com.party.organization.entity.OrganizationUnit;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
@Entity @Table(name="gallery_entries", indexes={@Index(name="idx_gallery_public",columnList="published,deleted,event_date"), @Index(name="idx_gallery_area",columnList="organization_unit_id")})
@Getter @Setter @NoArgsConstructor
public class GalleryEntry extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="organization_unit_id",nullable=false) private OrganizationUnit organizationUnit;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by",nullable=false) private AppUser createdBy;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="updated_by",nullable=false) private AppUser updatedBy;
    @Column(nullable=false,length=150) private String title;
    @Column(nullable=false,length=2000) private String description;
    @Column(name="event_date",nullable=false) private LocalDate eventDate;
    @Column(nullable=false) private boolean published;
    @Column(nullable=false) private boolean deleted;
    @Column(name="storage_key",nullable=false,length=500) private String storageKey;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
}
