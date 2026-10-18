package com.party.registration.dto;

import com.party.registration.entity.DocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/** Bind with @ModelAttribute from multipart/form-data, not @RequestBody JSON. */
@Getter
@Setter
public class ApplicationDocumentUploadRequest {
    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    @NotNull(message = "File is required")
    private MultipartFile file;
}
