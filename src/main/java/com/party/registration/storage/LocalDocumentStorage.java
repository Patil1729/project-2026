package com.party.registration.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Uses Java only, so file handling can also be checked without Spring or a DB. */
public final class LocalDocumentStorage implements DocumentStorage {
    private static final Pattern KEY = Pattern.compile(
            "[1-9][0-9]*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|png|jpg)");
    private static final byte[] PNG_HEADER = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final Path root;
    private final long maxFileBytes;

    public LocalDocumentStorage(Path root, long maxFileBytes) throws IOException {
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("Maximum file size must be positive");
        }
        Files.createDirectories(root.toAbsolutePath().normalize());
        this.root = root.toAbsolutePath().normalize().toRealPath();
        this.maxFileBytes = maxFileBytes;
    }

    @Override
    public long maxFileBytes() {
        return maxFileBytes;
    }

    @Override
    public StoredDocumentFile store(long applicationId, String originalFilename, InputStream input)
            throws IOException {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("Application ID must be positive");
        }
        Objects.requireNonNull(input, "File input is required");
        String filename = cleanFilename(originalFilename);
        String extension = extension(filename);
        if (!extension.equals("pdf") && !extension.equals("png")
                && !extension.equals("jpg") && !extension.equals("jpeg")) {
            throw rejected(DocumentFileException.Reason.UNSUPPORTED_FILE_TYPE,
                    "Only PDF, PNG, JPG, and JPEG files are supported.");
        }

        Path directory = root.resolve(Long.toString(applicationId));
        Files.createDirectories(directory);
        requireSafeDirectory(directory);
        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            MessageDigest digest = sha256Digest();
            long size = 0;
            try (OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    if (read > maxFileBytes - size) {
                        throw rejected(DocumentFileException.Reason.FILE_TOO_LARGE,
                                "The file exceeds the configured maximum size.");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    size += read;
                }
            }
            if (size == 0) {
                throw rejected(DocumentFileException.Reason.EMPTY_FILE, "Select a nonempty file.");
            }

            byte[] header;
            try (InputStream saved = Files.newInputStream(temporary)) {
                header = saved.readNBytes(8);
            }
            String contentType = detectContentType(header);
            String storedExtension = switch (contentType) {
                case "application/pdf" -> "pdf";
                case "image/png" -> "png";
                default -> "jpg";
            };
            String normalizedExtension = extension.equals("jpeg") ? "jpg" : extension;
            if (!storedExtension.equals(normalizedExtension)) {
                throw rejected(DocumentFileException.Reason.UNSUPPORTED_FILE_TYPE,
                        "The filename extension does not match the file contents.");
            }

            String storageKey = applicationId + "/" + UUID.randomUUID() + "." + storedExtension;
            // The user-supplied name is metadata only and never becomes a disk path.
            Files.move(temporary, root.resolve(storageKey));
            return new StoredDocumentFile(filename, storageKey, contentType, size,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        Path file = resolveKey(storageKey);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException("Document content is unavailable");
        }
        return Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveKey(storageKey));
    }

    private Path resolveKey(String storageKey) throws IOException {
        if (storageKey == null || !KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        if (Files.exists(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            requireSafeDirectory(file.getParent());
        }
        return file;
    }

    private void requireSafeDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !directory.toRealPath().startsWith(root)) {
            throw new IOException("Document directory is not a private storage directory");
        }
    }

    private static String cleanFilename(String supplied) throws DocumentFileException {
        if (supplied == null) {
            throw rejected(DocumentFileException.Reason.INVALID_FILENAME, "A filename is required.");
        }
        String name = supplied.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isBlank() || name.length() > 255) {
            throw rejected(DocumentFileException.Reason.INVALID_FILENAME,
                    "The filename must contain 1 to 255 characters.");
        }
        return name;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String detectContentType(byte[] header) throws DocumentFileException {
        if (header.length >= 5 && header[0] == '%' && header[1] == 'P'
                && header[2] == 'D' && header[3] == 'F' && header[4] == '-') {
            return "application/pdf";
        }
        if (header.length >= 8 && Arrays.equals(Arrays.copyOf(header, 8), PNG_HEADER)) {
            return "image/png";
        }
        if (header.length >= 3 && (header[0] & 255) == 255
                && (header[1] & 255) == 216 && (header[2] & 255) == 255) {
            return "image/jpeg";
        }
        throw rejected(DocumentFileException.Reason.UNSUPPORTED_FILE_TYPE,
                "File contents must have a PDF, PNG, or JPEG signature.");
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static DocumentFileException rejected(DocumentFileException.Reason reason, String message) {
        return new DocumentFileException(reason, message);
    }
}
