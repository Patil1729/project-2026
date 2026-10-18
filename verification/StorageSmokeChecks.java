import com.party.registration.storage.DocumentFileException;
import com.party.registration.storage.LocalDocumentStorage;
import com.party.registration.storage.StoredDocumentFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;

/** Real filesystem checks that need only JDK 17; no Spring, Maven, or database. */
public class StorageSmokeChecks {
    private static int passed;

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("legalcell-document-checks-");
        try {
            Path root = work.resolve("private");
            LocalDocumentStorage storage = new LocalDocumentStorage(root, 1024);
            byte[] pdf = "%PDF-1.4\nlocal-storage-check\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
            StoredDocumentFile first = storage.store(1, "proof.pdf", new ByteArrayInputStream(pdf));

            check("PDF content round trip", () -> {
                try (InputStream input = storage.open(first.storageKey())) {
                    require(Arrays.equals(pdf, input.readAllBytes()));
                }
            });
            check("size, MIME, and checksum metadata", () -> {
                require(first.sizeBytes() == pdf.length);
                require(first.contentType().equals("application/pdf"));
                require(first.sha256().equals(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(pdf))));
            });
            check("generated private filename", () -> {
                require(first.storageKey().startsWith("1/"));
                require(!first.storageKey().contains("proof"));
                require(Files.exists(root.resolve(first.storageKey())));
            });
            check("Windows filename becomes metadata basename", () -> {
                StoredDocumentFile stored = storage.store(1, "C:\\fakepath\\proof.pdf", new ByteArrayInputStream(pdf));
                require(stored.originalFilename().equals("proof.pdf"));
            });
            check("path segments cannot choose the storage location", () -> {
                StoredDocumentFile stored = storage.store(2, "../../outside.pdf", new ByteArrayInputStream(pdf));
                require(stored.originalFilename().equals("outside.pdf"));
                require(stored.storageKey().startsWith("2/"));
                require(!Files.exists(work.resolve("outside.pdf")));
            });
            check("PNG signature recognition", () -> {
                byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};
                StoredDocumentFile stored = storage.store(1, "image.PNG", new ByteArrayInputStream(png));
                require(stored.contentType().equals("image/png"));
            });
            check("JPEG signature and extension normalization", () -> {
                byte[] jpeg = {(byte) 255, (byte) 216, (byte) 255, (byte) 224, 0};
                StoredDocumentFile stored = storage.store(1, "photo.JPEG", new ByteArrayInputStream(jpeg));
                require(stored.contentType().equals("image/jpeg"));
                require(stored.storageKey().endsWith(".jpg"));
            });
            check("empty upload rejected without leftover file", () -> {
                long before = files(root);
                rejected(DocumentFileException.Reason.EMPTY_FILE,
                        () -> storage.store(1, "empty.pdf", new ByteArrayInputStream(new byte[0])));
                require(files(root) == before);
            });
            check("oversized stream rejected without leftover file", () -> {
                long before = files(root);
                byte[] oversized = Arrays.copyOf(pdf, 1025);
                rejected(DocumentFileException.Reason.FILE_TOO_LARGE,
                        () -> storage.store(1, "large.pdf", new ByteArrayInputStream(oversized)));
                require(files(root) == before);
            });
            check("unsupported extension rejected", () -> rejected(
                    DocumentFileException.Reason.UNSUPPORTED_FILE_TYPE,
                    () -> storage.store(1, "program.exe", new ByteArrayInputStream(pdf))));
            check("renaming content cannot bypass the type check", () -> rejected(
                    DocumentFileException.Reason.UNSUPPORTED_FILE_TYPE,
                    () -> storage.store(1, "fake.jpg", new ByteArrayInputStream(pdf))));
            check("invalid file contents rejected", () -> rejected(
                    DocumentFileException.Reason.UNSUPPORTED_FILE_TYPE,
                    () -> storage.store(1, "fake.pdf", new ByteArrayInputStream(new byte[]{1, 2, 3}))));
            check("unsafe storage key rejected", () -> {
                try {
                    storage.open("../outside.pdf");
                    throw new AssertionError("Expected invalid key rejection");
                } catch (IllegalArgumentException expected) {
                    // Expected.
                }
            });
            check("interrupted copy removes temporary content", () -> {
                long before = files(root);
                InputStream broken = new InputStream() {
                    private boolean firstRead = true;
                    @Override public int read() throws IOException { throw new IOException("Interrupted"); }
                    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                        if (!firstRead) { throw new IOException("Interrupted"); }
                        firstRead = false;
                        System.arraycopy(pdf, 0, bytes, offset, 5);
                        return 5;
                    }
                };
                try {
                    storage.store(1, "interrupted.pdf", broken);
                    throw new AssertionError("Expected interrupted copy failure");
                } catch (IOException expected) {
                    require(files(root) == before);
                }
            });
            check("delete is idempotent and preserves other files", () -> {
                StoredDocumentFile other = storage.store(1, "other.pdf", new ByteArrayInputStream(pdf));
                storage.delete(other.storageKey());
                storage.delete(other.storageKey());
                require(!Files.exists(root.resolve(other.storageKey())));
                require(Files.exists(root.resolve(first.storageKey())));
            });
            check("symlinked application directory cannot escape the root", () -> {
                Path outside = Files.createDirectory(work.resolve("outside"));
                Files.createSymbolicLink(root.resolve("99"), outside);
                try {
                    storage.store(99, "escape.pdf", new ByteArrayInputStream(pdf));
                    throw new AssertionError("Expected unsafe directory rejection");
                } catch (IOException expected) {
                    require(files(outside) == 0);
                }
            });
            System.out.println("PASS: " + passed + " real-filesystem checks");
        } finally {
            try (var paths = Files.walk(work)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static long files(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).count();
        }
    }

    private static void rejected(DocumentFileException.Reason reason, Checked action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + reason);
        } catch (DocumentFileException exception) {
            require(exception.getReason() == reason);
        }
    }

    private static void check(String name, Checked action) throws Exception {
        action.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static void require(boolean condition) {
        if (!condition) { throw new AssertionError("Check failed"); }
    }

    @FunctionalInterface
    private interface Checked { void run() throws Exception; }
}
