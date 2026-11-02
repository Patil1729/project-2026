package com.party.gallery.controller;
import com.party.common.dto.PageResponse;
import com.party.community.storage.CommunityFiles;
import com.party.gallery.dto.*;
import com.party.gallery.service.GalleryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
@RestController @RequiredArgsConstructor
public class GalleryController {
    private final GalleryService service;
    @GetMapping("/api/public/gallery") public PageResponse<GalleryItem> publicItems(@RequestParam(required=false) @Positive Long areaId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="12") @Min(1) @Max(50) int size) {return service.list(true,areaId,page,size);}
    @GetMapping("/api/gallery") public ResponseEntity<PageResponse<GalleryItem>> managedItems(@RequestParam(required=false) @Positive Long areaId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="12") @Min(1) @Max(50) int size) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(false,areaId,page,size));}
    @GetMapping("/api/public/gallery/{id}/image") public ResponseEntity<Resource> image(@PathVariable @Positive Long id) {return content(service.content(id,true));}
    @GetMapping("/api/gallery/{id}/image") public ResponseEntity<Resource> preview(@PathVariable @Positive Long id) {return content(service.content(id,false));}
    @PostMapping(value="/api/gallery",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GalleryItem> create(@RequestParam @Positive Long organizationUnitId,@RequestParam @NotBlank @Size(max=150) String title,
        @RequestParam @NotBlank @Size(max=2000) String description,@RequestParam LocalDate eventDate,@RequestParam(defaultValue="false") boolean published,@RequestParam MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(organizationUnitId,title,description,eventDate,published,file));
    }
    @PutMapping("/api/gallery/{id}") public ResponseEntity<GalleryItem> update(@PathVariable @Positive Long id,@Valid @RequestBody GalleryMetadata data) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(id,data));}
    @PostMapping(value="/api/gallery/{id}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GalleryItem> replace(@PathVariable @Positive Long id,@RequestParam @PositiveOrZero Long version,@RequestParam MultipartFile file) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.replaceImage(id,version,file));}
    @DeleteMapping("/api/gallery/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Long id,@RequestParam @PositiveOrZero Long version) {service.delete(id,version);return ResponseEntity.noContent().build();}
    private ResponseEntity<Resource> content(CommunityFiles.Content data) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(data.size()).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(new InputStreamResource(data.input()));
    }
}
