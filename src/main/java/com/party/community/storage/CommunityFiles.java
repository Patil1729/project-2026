package com.party.community.storage;

import com.party.registration.storage.*;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.picture.storage.ProfileImageNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;

/** Gallery images and confidential evidence have separate roots. Files never become static resources. */
@Component @Slf4j
public class CommunityFiles {
    private final LocalDocumentStorage gallery;
    private final LocalDocumentStorage evidence;
    private final ProfileImageNormalizer images;
    private final long maxBytes;
    public record Content(InputStream input,long size,String contentType,String filename) { }
    public CommunityFiles(ProfileImageNormalizer images,
            @Value("${app.gallery.storage.root:./private-storage/gallery}") String galleryRoot,
            @Value("${app.help.evidence.root:./private-storage/help-evidence}") String evidenceRoot,
            @Value("${app.community.max-file-bytes:5242880}") long maxBytes) throws IOException {
        if (maxBytes < 1 || maxBytes > 5242880) throw new IllegalArgumentException("Community upload limit must be between 1 and 5242880 bytes");
        this.images=images; this.maxBytes=maxBytes;
        gallery=new LocalDocumentStorage(Path.of(galleryRoot),12582912);
        evidence=new LocalDocumentStorage(Path.of(evidenceRoot),maxBytes);
    }
    public StoredDocumentFile image(long id,MultipartFile file) {
        var image=images.normalize(file,maxBytes,1600);
        try { var saved=gallery.store(id,"gallery.png",new ByteArrayInputStream(image.bytes())); rollback(gallery,saved.storageKey()); return saved; }
        catch(IOException exception) { throw failure(exception); }
    }
    public StoredDocumentFile evidence(long id,MultipartFile file) {
        if(file==null || file.isEmpty()) throw new RegistrationException(HttpStatus.BAD_REQUEST,"EMPTY_EVIDENCE","Select a nonempty JPG, PNG or PDF file.");
        if(file.getSize()>maxBytes) throw new RegistrationException(HttpStatus.CONTENT_TOO_LARGE,"EVIDENCE_TOO_LARGE","Each evidence file must be at most 5 MB.");
        try(var input=file.getInputStream()) { var saved=evidence.store(id,file.getOriginalFilename(),input); rollback(evidence,saved.storageKey()); return saved; }
        catch(IOException exception) { throw failure(exception); }
    }
    public Content galleryContent(String key,long size) { return open(gallery,key,size,"image/png","gallery.png"); }
    public Content evidenceContent(String key,long size,String type,String filename) { return open(evidence,key,size,type,filename); }
    public void removeGalleryAfterCommit(String key) { afterCommit(() -> cleanup(gallery,key)); }
    private Content open(LocalDocumentStorage store,String key,long size,String type,String filename) {
        try { return new Content(store.open(key),size,type,filename); }
        catch(NoSuchFileException exception) { throw new RegistrationException(HttpStatus.NOT_FOUND,"FILE_NOT_FOUND","The stored file is unavailable. Contact the coordinator."); }
        catch(IOException exception) { throw failure(exception); }
    }
    private void rollback(LocalDocumentStorage store,String key) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCompletion(int status) { if(status!=STATUS_COMMITTED) cleanup(store,key); }
        });
    }
    private void afterCommit(Runnable action) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { public void afterCommit() { action.run(); } });
        else action.run();
    }
    private void cleanup(LocalDocumentStorage store,String key) { try {store.delete(key);} catch(IOException | RuntimeException exception) {log.warn("Stored community file retained for maintenance",exception);} }
    private RegistrationException failure(IOException exception) {
        if(exception instanceof DocumentFileException invalid) return new RegistrationException(
            invalid.getReason()==DocumentFileException.Reason.FILE_TOO_LARGE?HttpStatus.CONTENT_TOO_LARGE:HttpStatus.BAD_REQUEST,"INVALID_COMMUNITY_FILE",invalid.getMessage());
        return new RegistrationException(HttpStatus.INTERNAL_SERVER_ERROR,"COMMUNITY_STORAGE_FAILURE","The file could not be stored or loaded. Try again.");
    }
}
