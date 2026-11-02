package com.party.user.picture.storage;
import com.party.registration.storage.LocalDocumentStorage;
import com.party.registration.storage.StoredDocumentFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.Path;

@Component
public class ProfilePictureStorage {
    private final LocalDocumentStorage delegate;
    private final long maxBytes;
    public ProfilePictureStorage(@Value("${app.user.pictures.root:./private-storage/user-pictures}") String root,
            @Value("${app.user.pictures.max-file-bytes:2097152}") long maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("Profile picture limit must be positive");
        this.maxBytes = maxBytes;
        delegate = new LocalDocumentStorage(Path.of(root), Math.max(maxBytes, 2097152));
    }
    public long maxBytes() { return maxBytes; }
    public StoredDocumentFile store(long userId, byte[] bytes) throws IOException {
        return delegate.store(userId, "avatar.png", new ByteArrayInputStream(bytes));
    }
    public InputStream open(String key) throws IOException { return delegate.open(key); }
    public void delete(String key) throws IOException { delegate.delete(key); }
}
