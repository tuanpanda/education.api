package com.education.base.storage;

import com.education.base.common.file.FileConstants;
import com.education.base.config.FileStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * {@link BlobStore} trên đĩa cục bộ / volume Docker ({@code app.storage.base-dir}).
 * <p>
 * Ghi nguyên tử: stream vào {@code {base}/.tmp/{uuid}} qua {@link DigestInputStream} (SHA-256), kiểm tra kích thước,
 * rồi {@code Files.move(ATOMIC_MOVE)} vào đích. Mọi key được chuẩn hoá và bắt buộc nằm trong {@code base}
 * (chặn path traversal); {@code .tmp/} và {@code .quarantine/} bị bỏ qua khi {@link #list(String)}.
 */
@Slf4j
@Component
public class LocalDiskBlobStore implements BlobStore {

    static final String TMP_DIR = ".tmp";
    static final String QUARANTINE_DIR = ".quarantine";

    private final FileStorageProperties properties;

    public LocalDiskBlobStore(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public BlobInfo put(String key, InputStream in, long expectedSize) throws IOException {
        Path target = resolve(key);
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException(key);
        }
        Path base = base();
        Path tmpDir = base.resolve(TMP_DIR);
        Files.createDirectories(tmpDir);
        Path tmp = tmpDir.resolve(UUID.randomUUID().toString());
        MessageDigest digest = sha256();
        long size;
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                DigestInputStream din = new DigestInputStream(in, digest);
                size = din.transferTo(out);
            }
            if (expectedSize >= 0 && size != expectedSize) {
                throw new IOException("Blob size mismatch: expected " + expectedSize + ", got " + size);
            }
            Files.createDirectories(target.getParent());
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return new BlobInfo(key, size, HexFormat.of().formatHex(digest.digest()), backend());
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(existing(key));
    }

    @Override
    public Resource resource(String key) throws IOException {
        return new PathResource(existing(key));
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(existing(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public String moveToQuarantine(String key) throws IOException {
        Path source = existing(key);
        String newKey = QUARANTINE_DIR + "/" + normalizeKey(key);
        Path target = resolveInternal(newKey);
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return newKey;
    }

    @Override
    public Stream<BlobEntry> list(String prefix) throws IOException {
        Path base = base();
        Path root = prefix == null || prefix.isBlank() ? base : resolve(prefix);
        if (!Files.isDirectory(root)) {
            return Stream.empty();
        }
        Path tmp = base.resolve(TMP_DIR);
        Path quarantine = base.resolve(QUARANTINE_DIR);
        return Files.walk(root)
                .filter(p -> !p.startsWith(tmp) && !p.startsWith(quarantine))
                .filter(Files::isRegularFile)
                .map(p -> {
                    try {
                        return new BlobEntry(base.relativize(p).toString().replace('\\', '/'),
                                Files.size(p), Files.getLastModifiedTime(p).toInstant());
                    } catch (IOException e) {
                        log.warn("Cannot stat blob {}: {}", p, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull);
    }

    @Override
    public StorageUsage usage() throws IOException {
        Path base = base();
        Files.createDirectories(base);
        FileStore store = Files.getFileStore(base);
        return new StorageUsage(backend(), store.getTotalSpace(), store.getUsableSpace(), -1);
    }

    @Override
    public String backend() {
        return FileConstants.StorageBackend.LOCAL;
    }

    // ---------------------------------------------------------------------------------------------

    private Path existing(String key) throws IOException {
        Path p = resolve(key);
        if (!Files.isRegularFile(p)) {
            throw new NoSuchFileException(key);
        }
        return p;
    }

    /** Key của người dùng: không được trỏ vào vùng nội bộ {@code .tmp/}, {@code .quarantine/}. */
    private Path resolve(String key) {
        String k = normalizeKey(key);
        String first = k.split("/", 2)[0];
        if (TMP_DIR.equals(first) || QUARANTINE_DIR.equals(first)) {
            throw new IllegalArgumentException("Reserved storage key");
        }
        return resolveInternal(k);
    }

    private Path resolveInternal(String key) {
        Path base = base();
        Path p = base.resolve(key).toAbsolutePath().normalize();
        if (!p.startsWith(base) || p.equals(base)) {
            log.error("Path traversal detected for storage key '{}'", key);
            throw new IllegalArgumentException("Invalid storage key");
        }
        return p;
    }

    static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Empty storage key");
        }
        String k = key.replace('\\', '/');
        for (int i = 0; i < k.length(); i++) {
            if (Character.isISOControl(k.charAt(i))) {
                throw new IllegalArgumentException("Invalid storage key");
            }
        }
        if (k.startsWith("/") || k.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Absolute storage key");
        }
        for (String part : k.split("/")) {
            if ("..".equals(part)) {
                throw new IllegalArgumentException("Invalid storage key");
            }
        }
        return k;
    }

    private Path base() {
        String dir = properties.getBaseDir() == null ? "./outputs" : properties.getBaseDir();
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
