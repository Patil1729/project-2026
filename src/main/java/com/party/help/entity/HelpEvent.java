package com.party.help.entity;
import com.party.common.entity.AuditableEntity;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="help_events",indexes=@Index(name="idx_help_event_request",columnList="help_request_id,id"))
@Getter @Setter @NoArgsConstructor
public class HelpEvent extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="help_request_id",nullable=false) private HelpRequest helpRequest;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false) private AppUser actor;
    @Column(nullable=false,length=40) private String action;
    @Column(nullable=false,length=2000) private String note;
    @Column(name="from_advocate",length=150) private String fromAdvocate;
    @Column(name="to_advocate",length=150) private String toAdvocate;
}
