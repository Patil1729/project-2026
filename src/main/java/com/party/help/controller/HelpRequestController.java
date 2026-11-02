package com.party.help.controller;
import com.party.common.dto.PageResponse;
import com.party.help.dto.HelpDtos.*;
import com.party.help.entity.HelpStatus;
import com.party.help.service.HelpRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/help-requests") @RequiredArgsConstructor
public class HelpRequestController {
    private final HelpRequestService service;
    @PostMapping public ResponseEntity<Detail> create(@Valid @RequestBody Create data) {return result(service.create(data),HttpStatus.CREATED);}
    @GetMapping public ResponseEntity<PageResponse<Summary>> list(@RequestParam(defaultValue="mine") String view,
        @RequestParam(required=false) HelpStatus status,@RequestParam(required=false) Boolean urgent,@RequestParam(required=false) @Size(max=100) String q,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="12") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(view,status,urgent,q,page,size));
    }
    @GetMapping("/{id}") public ResponseEntity<Detail> get(@PathVariable @Positive Long id) {return result(service.get(id),HttpStatus.OK);}
    @GetMapping("/{id}/history") public ResponseEntity<PageResponse<Event>> history(@PathVariable @Positive Long id,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(id,page,size));
    }
    @GetMapping("/{id}/candidates") public ResponseEntity<List<Candidate>> candidates(@PathVariable @Positive Long id,@RequestParam(defaultValue="false") boolean senior) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.candidates(id,senior));
    }
    @PostMapping("/{id}/assign") public ResponseEntity<Detail> assign(@PathVariable @Positive Long id,@Valid @RequestBody Transfer data) {return result(service.assign(id,data,false),HttpStatus.OK);}
    @PostMapping("/{id}/transfer") public ResponseEntity<Detail> transfer(@PathVariable @Positive Long id,@Valid @RequestBody Transfer data) {return result(service.assign(id,data,true),HttpStatus.OK);}
    @PostMapping("/{id}/actions") public ResponseEntity<Detail> action(@PathVariable @Positive Long id,@Valid @RequestBody Action data) {return result(service.action(id,data),HttpStatus.OK);}
    @PostMapping("/{id}/comments") public ResponseEntity<Detail> comment(@PathVariable @Positive Long id,@Valid @RequestBody Comment data) {return result(service.comment(id,data),HttpStatus.OK);}
    @PostMapping(value="/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Detail> upload(@PathVariable @Positive Long id,@RequestParam @PositiveOrZero Long version,@RequestParam MultipartFile file) {return result(service.upload(id,version,file),HttpStatus.OK);}
    @GetMapping("/{id}/attachments/{attachmentId}") public ResponseEntity<Resource> download(@PathVariable @Positive Long id,@PathVariable @Positive Long attachmentId) {
        var data=service.download(id,attachmentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(data.contentType())).contentLength(data.size())
            .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(data.filename(),StandardCharsets.UTF_8).build().toString())
            .body(new InputStreamResource(data.input()));
    }
    private ResponseEntity<Detail> result(Detail detail,HttpStatus status) {return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(detail);}
}
