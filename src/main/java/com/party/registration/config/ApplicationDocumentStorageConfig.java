package com.party.registration.config;

import com.party.registration.storage.DocumentStorage;
import com.party.registration.storage.LocalDocumentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class ApplicationDocumentStorageConfig {
    @Bean
    public DocumentStorage applicationDocumentStorage(
            @Value("${app.registration.documents.root:./private-storage/registration-documents}") String root,
            @Value("${app.registration.documents.max-file-bytes:5242880}") long maxFileBytes) throws IOException {
        return new LocalDocumentStorage(Path.of(root), maxFileBytes);
    }
}
