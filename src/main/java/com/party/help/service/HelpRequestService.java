package com.party.help.service;

import com.party.committee.entity.AdvocateAssignment;
import com.party.common.dto.PageResponse;
import com.party.community.access.CommunityAccessService;
import com.party.community.access.CommunityAccessService.Scope;
import com.party.community.storage.CommunityFiles;
import com.party.help.dto.HelpDtos.*;
import com.party.help.entity.*;
import com.party.help.repository.*;
import com.party.organization.entity.*;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class HelpRequestService {
    private static final Set<HelpStatus> OPEN=Set.of(HelpStatus.UNASSIGNED,HelpStatus.ASSIGNED,HelpStatus.IN_PROGRESS);
    private final HelpRequestRepository requests;
    private final HelpEventRepository events;
    private final HelpAttachmentRepository attachments;
    private final OrganizationUnitRepository organizations;
    private final UserRepository users;
    private final CommunityAccessService access;
    private final CommunityFiles files;
    private final Clock clock;

    @Transactional(isolation=Isolation.READ_COMMITTED) public Detail create(Create data) {
        var scope=access.current();
        // Serialize a caller's duplicate submissions and open-case quota checks.
        var actor=users.findByIdForUpdate(scope.actorId()).orElseThrow(this::missing);
        var existing=requests.findByRequester_IdAndSubmissionKey(actor.getId(),data.submissionKey().toString());
        if(existing.isPresent()) return detail(existing.get(),scope);
        if(requests.countByRequester_IdAndStatusIn(actor.getId(),OPEN)>=20) throw error(HttpStatus.CONFLICT,"HELP_OPEN_LIMIT","You already have 20 open help requests. Continue an existing request first.");
        var origin=organizations.findById(data.organizationUnitId()).orElseThrow(CommunityAccessService::invalidArea);
        if(origin.getType()==OrganizationType.PARTY || !CommunityAccessService.validHierarchy(origin)) throw CommunityAccessService.invalidArea();
        var h=new HelpRequest();h.setRequester(actor);h.setOrganizationUnit(origin);h.setRoutingUnit(origin);
        h.setSubmissionKey(data.submissionKey().toString());
        h.setReference("LC-"+LocalDate.ofInstant(clock.instant(),ZoneId.of("Asia/Kolkata")).getYear()+"-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT));
        h.setContactName(data.contactName().strip());h.setContactMobile(data.contactMobile().strip());h.setLocality(data.locality().strip());
        h.setSubject(data.subject().strip());h.setDescription(data.description().strip());h.setCategory(data.category());h.setUrgent(data.urgent());
        h.setStatus(HelpStatus.UNASSIGNED);h.setConsentAt(clock.instant());h.setLastActivityAt(clock.instant());
        var assigned=best(h,scope,false,null);
        if(assigned!=null) assignTo(h,assigned);
        requests.saveAndFlush(h);
        event(h,scope,"CREATED",assigned==null?"Request received. No current committee member is available; awaiting coordinator assignment.":"Request received and routed to the nearest available committee.",null,assigned);
        return detail(h,scope);
    }

    public PageResponse<Summary> list(String view,HelpStatus status,Boolean urgent,String search,int page,int size) {
        var scope=access.current();
        if(!Set.of("mine","inbox").contains(view)) throw error(HttpStatus.BAD_REQUEST,"INVALID_HELP_VIEW","Use mine or inbox.");
        var ownIds=scope.ownAppointments().stream().map(AdvocateAssignment::getId).toList();
        var coordinated=scope.coordinatorUnits();
        if(view.equals("inbox") && !scope.global() && ownIds.isEmpty() && coordinated.isEmpty()) throw CommunityAccessService.denied();
        Specification<HelpRequest> spec=(r,q,c)-> {
            if(view.equals("mine")) return c.equal(r.get("requester").get("id"),scope.actorId());
            if(scope.global()) return c.conjunction();
            List<Predicate> choices=new ArrayList<>();
            if(!ownIds.isEmpty()) choices.add(r.get("assignedAppointment").get("id").in(ownIds));
            if(!coordinated.isEmpty()) choices.add(r.get("organizationUnit").get("id").in(coordinated));
            return c.or(choices.toArray(Predicate[]::new));
        };
        if(status!=null) spec=spec.and((r,q,c)->c.equal(r.get("status"),status));
        if(urgent!=null) spec=spec.and((r,q,c)->c.equal(r.get("urgent"),urgent));
        if(search!=null && !search.isBlank()) {
            String term="%"+search.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            spec=spec.and((r,q,c)->c.or(c.like(c.lower(r.get("subject")),term,'!'),c.like(c.lower(r.get("reference")),term,'!')));
        }
        var result=requests.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Order.desc("urgent"),Sort.Order.desc("lastActivityAt"),Sort.Order.desc("id"))));
        return PageResponse.from(result.map(h->summary(h,scope)));
    }

    public Detail get(Long id) {var scope=access.current();var h=find(id);requireRead(h,scope);return detail(h,scope);}
    public PageResponse<Event> history(Long id,int page,int size) {
        var scope=access.current();var h=find(id);requireRead(h,scope);
        return PageResponse.from(events.findByHelpRequest_Id(id,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")))
            .map(e->new Event(e.getId(),e.getAction(),e.getNote(),e.getActor().getFullName(),e.getFromAdvocate(),e.getToAdvocate(),e.getCreatedAt())));
    }

    public List<Candidate> candidates(Long id,boolean senior) {
        var scope=access.current();var h=find(id);requireRead(h,scope);
        if(senior) {if(!canTransfer(h,scope)) throw CommunityAccessService.denied();}
        else if(!coordinate(h,scope) || !OPEN.contains(h.getStatus())) throw CommunityAccessService.denied();
        var loads=loads();
        return candidates(h,scope,senior).stream().map(a->new Candidate(a.getId(),a.getAdvocate().getId(),a.getAdvocate().getFullName(),
                a.getCommitteePosition().getCommittee().getOrganizationUnit().getId(),a.getCommitteePosition().getCommittee().getOrganizationUnit().getName(),
                a.getCommitteePosition().getPositionType(),loads.getOrDefault(a.getAdvocate().getId(),0L))).toList();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED) public Detail assign(Long id,Transfer data,boolean transfer) {
        var scope=access.current();var h=locked(id,data.version(),scope);requireRead(h,scope);
        if(transfer) {if(!canTransfer(h,scope)) throw CommunityAccessService.denied();}
        else if(!coordinate(h,scope) || !OPEN.contains(h.getStatus())) throw CommunityAccessService.denied();
        var next=best(h,scope,transfer,data.toAssignmentId());
        if(next==null) throw error(HttpStatus.CONFLICT,transfer?"NO_SENIOR_AVAILABLE":"NO_ADVOCATE_AVAILABLE",
            transfer?"No eligible member is available in a higher committee. Your current assignment remains in place; contact the senior coordinator.":"No eligible current committee member is available in this area or its parent committees.");
        var previous=h.getAssignedAppointment();
        if(previous!=null && Objects.equals(previous.getId(),next.getId())) throw error(HttpStatus.CONFLICT,"ALREADY_ASSIGNED","This committee member is already assigned.");
        assignTo(h,next);touch(h);requests.saveAndFlush(h);
        event(h,scope,transfer?"TRANSFERRED":"ASSIGNED",data.note().strip(),previous,next);
        return detail(h,scope);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED) public Detail action(Long id,Action data) {
        var scope=access.current();var h=locked(id,data.version(),scope);requireRead(h,scope);
        switch(data.action().toUpperCase(Locale.ROOT)) {
            case "START" -> {if(!handler(h,scope) || h.getStatus()!=HelpStatus.ASSIGNED) throw transition();h.setStatus(HelpStatus.IN_PROGRESS);}
            case "RESOLVE" -> {if(!handler(h,scope) || !Set.of(HelpStatus.ASSIGNED,HelpStatus.IN_PROGRESS).contains(h.getStatus())) throw transition();
                h.setStatus(HelpStatus.RESOLVED);h.setResolutionNote(data.note().strip());h.setResolvedAt(clock.instant());}
            case "CLOSE" -> {if(!(owner(h,scope)||coordinate(h,scope)) || h.getStatus()!=HelpStatus.RESOLVED) throw transition();h.setStatus(HelpStatus.CLOSED);}
            case "REOPEN" -> {if(!owner(h,scope) || !Set.of(HelpStatus.RESOLVED,HelpStatus.CLOSED).contains(h.getStatus())) throw transition();
                users.findByIdForUpdate(scope.actorId()).orElseThrow(this::missing);
                if(requests.countByRequester_IdAndStatusIn(scope.actorId(),OPEN)>=20) throw error(HttpStatus.CONFLICT,"HELP_OPEN_LIMIT","Continue an existing open request first.");
                h.setStatus(HelpStatus.UNASSIGNED);h.setResolutionNote(null);h.setResolvedAt(null);
                var next=best(h,scope,false,null);h.setAssignedAppointment(null);if(next!=null) assignTo(h,next);}
            default -> throw error(HttpStatus.BAD_REQUEST,"INVALID_HELP_ACTION","Use START, RESOLVE, CLOSE or REOPEN.");
        }
        touch(h);requests.saveAndFlush(h);event(h,scope,data.action().toUpperCase(Locale.ROOT),data.note().strip(),null,h.getAssignedAppointment());return detail(h,scope);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED) public Detail comment(Long id,Comment data) {
        var scope=access.current();var h=locked(id,data.version(),scope);requireRead(h,scope);
        if(!canComment(h,scope)) throw CommunityAccessService.denied();
        touch(h);requests.saveAndFlush(h);event(h,scope,"COMMENT",data.note().strip(),null,null);return detail(h,scope);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED) public Detail upload(Long id,Long version,MultipartFile file) {
        var scope=access.current();var h=locked(id,version,scope);requireRead(h,scope);
        if(!canComment(h,scope)) throw CommunityAccessService.denied();
        if(attachments.countByHelpRequest_Id(id)>=5) throw error(HttpStatus.CONFLICT,"HELP_ATTACHMENT_LIMIT","A help request can contain at most five evidence files.");
        var stored=files.evidence(id,file);var a=new HelpAttachment();a.setHelpRequest(h);a.setUploadedBy(users.getReferenceById(scope.actorId()));
        a.setOriginalFilename(stored.originalFilename());a.setStorageKey(stored.storageKey());a.setContentType(stored.contentType());a.setSizeBytes(stored.sizeBytes());attachments.save(a);
        touch(h);requests.saveAndFlush(h);event(h,scope,"EVIDENCE_ADDED","Evidence added: "+stored.originalFilename(),null,null);return detail(h,scope);
    }
    public CommunityFiles.Content download(Long requestId,Long attachmentId) {
        var scope=access.current();var h=find(requestId);requireRead(h,scope);
        var a=attachments.findByIdAndHelpRequest_Id(attachmentId,requestId).orElseThrow(this::missing);
        return files.evidenceContent(a.getStorageKey(),a.getSizeBytes(),a.getContentType(),a.getOriginalFilename());
    }

    private List<AdvocateAssignment> candidates(HelpRequest h,Scope scope,boolean senior) {
        var chain=CommunityAccessService.lineage(h.getRoutingUnit());
        Set<Long> allowed=chain.stream().skip(senior?1:0).map(OrganizationUnit::getId).collect(Collectors.toSet());
        var prior=h.getAssignedAppointment();
        Map<Long,Integer> depth=new HashMap<>();for(int i=0;i<chain.size();i++) depth.put(chain.get(i).getId(),i);
        var loads=loads();
        return scope.currentAppointments().stream()
            .filter(a->allowed.contains(a.getCommitteePosition().getCommittee().getOrganizationUnit().getId()))
            .filter(a->CommunityAccessService.covers(a,h.getOrganizationUnit()))
            .filter(a->!Objects.equals(a.getAdvocate().getId(),h.getRequester().getId()))
            .filter(a->!senior || prior==null || !Objects.equals(a.getAdvocate().getId(),prior.getAdvocate().getId()))
            .sorted(Comparator.<AdvocateAssignment>comparingInt(a->depth.get(a.getCommitteePosition().getCommittee().getOrganizationUnit().getId()))
                .thenComparingLong(a->loads.getOrDefault(a.getAdvocate().getId(),0L)).thenComparing(AdvocateAssignment::getId)).toList();
    }
    private AdvocateAssignment best(HelpRequest h,Scope scope,boolean senior,Long selected) {
        var candidates=candidates(h,scope,senior);
        if(selected==null) return candidates.stream().findFirst().orElse(null);
        return candidates.stream().filter(a->Objects.equals(a.getId(),selected)).findFirst()
            .orElseThrow(()->error(HttpStatus.BAD_REQUEST,"INVALID_HELP_ASSIGNEE","Choose a current committee member from this area's permitted reporting chain. Transfers must move to a higher committee."));
    }
    private Map<Long,Long> loads() {return requests.openLoads(OPEN).stream().collect(Collectors.toMap(row->((Number)row[0]).longValue(),row->((Number)row[1]).longValue()));}
    private void assignTo(HelpRequest h,AdvocateAssignment a) {h.setAssignedAppointment(a);h.setRoutingUnit(a.getCommitteePosition().getCommittee().getOrganizationUnit());h.setStatus(HelpStatus.ASSIGNED);}
    private boolean owner(HelpRequest h,Scope s) {return Objects.equals(h.getRequester().getId(),s.actorId());}
    private boolean handler(HelpRequest h,Scope s) {return h.getAssignedAppointment()!=null && !owner(h,s)
        && Objects.equals(h.getAssignedAppointment().getAdvocate().getId(),s.actorId()) && s.current(h.getAssignedAppointment(),h.getOrganizationUnit());}
    private boolean coordinate(HelpRequest h,Scope s) {return s.coordinator(h.getRoutingUnit());}
    private boolean canTransfer(HelpRequest h,Scope s) {return OPEN.contains(h.getStatus()) && (handler(h,s)||coordinate(h,s));}
    private boolean canComment(HelpRequest h,Scope s) {return h.getStatus()!=HelpStatus.CLOSED && (owner(h,s)||handler(h,s)||coordinate(h,s));}
    private void requireRead(HelpRequest h,Scope s) {
        if(!owner(h,s) && !handler(h,s) && !s.coordinator(h.getOrganizationUnit())) throw missing();
    }
    private HelpRequest find(Long id) {return requests.findById(id).orElseThrow(this::missing);}
    private HelpRequest locked(Long id,Long version,Scope scope) {
        var h=requests.lock(id).orElseThrow(this::missing);requireRead(h,scope);
        if(!Objects.equals(h.getVersion(),version)) throw error(HttpStatus.CONFLICT,"STALE_HELP_REQUEST","This request changed. Reload before continuing.");return h;
    }
    private Summary summary(HelpRequest h,Scope s) {
        var a=h.getAssignedAppointment();return new Summary(h.getId(),h.getReference(),h.getSubject(),h.getCategory(),h.getStatus(),h.isUrgent(),
            h.getOrganizationUnit().getName(),h.getRoutingUnit().getName(),a==null?null:a.getAdvocate().getFullName(),OPEN.contains(h.getStatus()) && !s.current(a,h.getOrganizationUnit()),h.getLastActivityAt());
    }
    private Detail detail(HelpRequest h,Scope s) {
        boolean work=handler(h,s),coordinator=coordinate(h,s),own=owner(h,s),comment=canComment(h,s);
        var actions=new Actions(coordinator && OPEN.contains(h.getStatus()),work && h.getStatus()==HelpStatus.ASSIGNED,
            work && Set.of(HelpStatus.ASSIGNED,HelpStatus.IN_PROGRESS).contains(h.getStatus()),canTransfer(h,s),
            (own||coordinator) && h.getStatus()==HelpStatus.RESOLVED,own && Set.of(HelpStatus.RESOLVED,HelpStatus.CLOSED).contains(h.getStatus()),comment,comment && attachments.countByHelpRequest_Id(h.getId())<5);
        return new Detail(summary(h,s),h.getVersion(),h.getRequester().getId(),h.getContactName(),h.getContactMobile(),h.getLocality(),h.getDescription(),h.getResolutionNote(),h.getResolvedAt(),actions,
            attachments.findByHelpRequest_IdOrderByIdAsc(h.getId()).stream().map(a->new Attachment(a.getId(),a.getOriginalFilename(),a.getContentType(),a.getSizeBytes())).toList());
    }
    private void touch(HelpRequest h) {h.setLastActivityAt(clock.instant());}
    private void event(HelpRequest h,Scope s,String action,String note,AdvocateAssignment from,AdvocateAssignment to) {
        var e=new HelpEvent();e.setHelpRequest(h);e.setActor(users.getReferenceById(s.actorId()));e.setAction(action);e.setNote(note);
        e.setFromAdvocate(from==null?null:from.getAdvocate().getFullName());e.setToAdvocate(to==null?null:to.getAdvocate().getFullName());events.saveAndFlush(e);
    }
    private RegistrationException transition() {return error(HttpStatus.CONFLICT,"INVALID_HELP_TRANSITION","This action is not allowed for your current appointment and the request's current status.");}
    private RegistrationException missing() {return error(HttpStatus.NOT_FOUND,"HELP_REQUEST_NOT_FOUND","Help request or evidence not found.");}
    private RegistrationException error(HttpStatus status,String code,String message) {return new RegistrationException(status,code,message);}
}
