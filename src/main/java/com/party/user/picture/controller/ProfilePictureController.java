package com.party.user.picture.controller;
import com.party.user.picture.dto.ProfilePictureResponse;
import com.party.user.picture.service.ProfilePictureService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequiredArgsConstructor @RequestMapping("/api/users/{userId}/profile-picture")
public class ProfilePictureController {
    private final ProfilePictureService service;
    @GetMapping public ResponseEntity<ProfilePictureResponse> get(@PathVariable("userId") @Positive Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
    }
    @GetMapping("/content") public ResponseEntity<Resource> content(@PathVariable("userId") @Positive Long id) {
        var picture = service.content(id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(picture.size()).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=profile.png")
            .body(new InputStreamResource(picture.input()));
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfilePictureResponse> upload(@PathVariable("userId") @Positive Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.upload(id, file));
    }
    @DeleteMapping public ResponseEntity<Void> remove(@PathVariable("userId") @Positive Long id) { service.remove(id); return ResponseEntity.noContent().build(); }
}
