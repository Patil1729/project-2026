package com.party.gallery.service;

import com.party.community.access.CommunityAccessService;
import com.party.community.storage.CommunityFiles;
import com.party.common.dto.PageResponse;
import com.party.gallery.dto.*;
import com.party.gallery.entity.GalleryEntry;
import com.party.gallery.repository.GalleryRepository;
import com.party.organization.entity.OrganizationType;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class GalleryService {
    private final GalleryRepository repository;
    private final OrganizationUnitRepository organizations;
    private final UserRepository users;
    private final CommunityAccessService access;
    private final CommunityFiles files;

    public PageResponse<GalleryItem> list(boolean publicOnly,Long area,int page,int size) {
        Set<Long> ids;
        if(publicOnly) ids=new HashSet<>(organizations.findAll().stream().filter(CommunityAccessService::validHierarchy).map(u->u.getId()).toList());
        else { var scope=access.current(); ids=scope.coordinatorUnits(); if(!scope.global() && ids.isEmpty()) throw CommunityAccessService.denied(); }
        Specification<GalleryEntry> spec=(r,q,c)->c.and(c.isFalse(r.get("deleted")),r.get("organizationUnit").get("id").in(ids));
        if(publicOnly) spec=spec.and((r,q,c)->c.isTrue(r.get("published")));
        if(area!=null) spec=spec.and((r,q,c)->c.equal(r.get("organizationUnit").get("id"),area));
        return PageResponse.from(repository.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Order.desc("eventDate"),Sort.Order.desc("id")))).map(this::item));
    }
    public CommunityFiles.Content content(Long id,boolean publicOnly) {
        var entry=repository.findById(id).filter(g->!g.isDeleted()).orElseThrow(this::missing);
        if(publicOnly) { if(!entry.isPublished() || !CommunityAccessService.validHierarchy(entry.getOrganizationUnit())) throw missing(); }
        else require(entry);
        return files.galleryContent(entry.getStorageKey(),entry.getSizeBytes());
    }
    @Transactional public GalleryItem create(Long organizationUnitId,String title,String description,LocalDate eventDate,boolean published,MultipartFile file) {
        var scope=access.current(); var unit=organizations.findById(organizationUnitId).orElseThrow(this::missing);
        if(unit.getType()==OrganizationType.PARTY || !CommunityAccessService.validHierarchy(unit)) throw CommunityAccessService.invalidArea();
        if(!scope.coordinator(unit)) throw CommunityAccessService.denied();
        var actor=users.getReferenceById(scope.actorId());
        var saved=files.image(unit.getId(),file);
        var entry=new GalleryEntry(); entry.setOrganizationUnit(unit);entry.setCreatedBy(actor);entry.setUpdatedBy(actor);
        entry.setTitle(title.strip());entry.setDescription(description.strip());entry.setEventDate(eventDate);entry.setPublished(published);
        entry.setStorageKey(saved.storageKey());entry.setSizeBytes(saved.sizeBytes());
        return item(repository.saveAndFlush(entry));
    }
    @Transactional public GalleryItem update(Long id,GalleryMetadata data) {
        var entry=locked(id,data.version());
        entry.setTitle(data.title().strip());entry.setDescription(data.description().strip());entry.setEventDate(data.eventDate());entry.setPublished(data.published());
        entry.setUpdatedBy(users.getReferenceById(access.current().actorId()));
        return item(repository.saveAndFlush(entry));
    }
    @Transactional public GalleryItem replaceImage(Long id,Long version,MultipartFile file) {
        var entry=locked(id,version);var saved=files.image(entry.getOrganizationUnit().getId(),file);
        files.removeGalleryAfterCommit(entry.getStorageKey());entry.setStorageKey(saved.storageKey());entry.setSizeBytes(saved.sizeBytes());
        entry.setUpdatedBy(users.getReferenceById(access.current().actorId()));return item(repository.saveAndFlush(entry));
    }
    @Transactional public void delete(Long id,Long version) {
        var entry=locked(id,version);entry.setDeleted(true);entry.setPublished(false);entry.setUpdatedBy(users.getReferenceById(access.current().actorId()));
        repository.saveAndFlush(entry);files.removeGalleryAfterCommit(entry.getStorageKey());
    }
    private GalleryEntry locked(Long id,Long version) { var entry=repository.lock(id).orElseThrow(this::missing);require(entry);
        if(!Objects.equals(version,entry.getVersion())) throw new RegistrationException(HttpStatus.CONFLICT,"STALE_GALLERY","The gallery item changed. Reload before editing.");return entry; }
    private void require(GalleryEntry entry) {if(!access.current().coordinator(entry.getOrganizationUnit())) throw CommunityAccessService.denied();}
    private GalleryItem item(GalleryEntry e) {return new GalleryItem(e.getId(),e.getOrganizationUnit().getId(),e.getOrganizationUnit().getName(),e.getTitle(),e.getDescription(),e.getEventDate(),e.isPublished(),e.getVersion(),e.getUpdatedAt());}
    private RegistrationException missing() {return new RegistrationException(HttpStatus.NOT_FOUND,"GALLERY_NOT_FOUND","Gallery item not found.");}
}
