package com.party.help.entity;
import com.party.common.entity.AuditableEntity;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="help_attachments",indexes=@Index(name="idx_help_attachment_request",columnList="help_request_id"))
@Getter @Setter @NoArgsConstructor
public class HelpAttachment extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="help_request_id",nullable=false) private HelpRequest helpRequest;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="uploaded_by",nullable=false) private AppUser uploadedBy;
    @Column(name="original_filename",nullable=false,length=255) private String originalFilename;
    @Column(name="storage_key",nullable=false,length=500) private String storageKey;
    @Column(name="content_type",nullable=false,length=100) private String contentType;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
}
