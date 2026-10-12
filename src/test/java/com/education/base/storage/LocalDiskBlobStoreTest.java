package com.education.base.storage;

import com.education.base.config.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskBlobStoreTest {

    @TempDir
    Path base;

    private LocalDiskBlobStore store;

    @BeforeEach
    void setUp() {
        FileStorageProperties props = new FileStorageProperties();
        props.setBaseDir(base.toString());
        store = new LocalDiskBlobStore(props);
    }

    @Test
    void put_writesAtomicallyAndReturnsSha256() throws Exception {
        BlobInfo info = store.put("STUDENT/2026/10/a.txt", in("abc"), 3);

        // SHA-256("abc")
        assertThat(info.checksumSha256())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(info.size()).isEqualTo(3);
        assertThat(info.backend()).isEqualTo("LOCAL");
        assertThat(Files.readString(base.resolve("STUDENT/2026/10/a.txt"))).isEqualTo("abc");
        assertThat(tmpFiles()).isEmpty();
        assertThat(store.exists("STUDENT/2026/10/a.txt")).isTrue();
        assertThat(store.size("STUDENT/2026/10/a.txt")).isEqualTo(3);
        try (InputStream s = store.open("STUDENT/2026/10/a.txt")) {
            assertThat(new String(s.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("abc");
        }
        assertThat(store.resource("STUDENT/2026/10/a.txt").contentLength()).isEqualTo(3);
    }

    @Test
    void put_sizeMismatch_leavesNothingBehind() {
        assertThatThrownBy(() -> store.put("A/x.txt", in("abcd"), 3)).isInstanceOf(IOException.class);
        assertThat(Files.exists(base.resolve("A/x.txt"))).isFalse();
        assertThat(tmpFiles()).isEmpty();
    }

    @Test
    void put_unknownSize_isAccepted() throws Exception {
        assertThat(store.put("A/y.txt", in("hello"), -1).size()).isEqualTo(5);
    }

    @Test
    void put_doesNotOverwriteExistingKey() throws Exception {
        store.put("A/z.txt", in("1"), 1);
        assertThatThrownBy(() -> store.put("A/z.txt", in("2"), 1)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(base.resolve("A/z.txt"))).isEqualTo("1");
    }

    @Test
    void put_doesNotCloseCallerStream() throws Exception {
        boolean[] closed = {false};
        InputStream s = new ByteArrayInputStream("q".getBytes()) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        store.put("A/q.txt", s, 1);
        assertThat(closed[0]).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.txt", "A/../../evil.txt", "/etc/passwd", "C:/Windows/x", "..\\x.txt",
            ".tmp/x", ".quarantine/x", "A/\u0000.txt", ""})
    void traversalAndReservedKeys_areRejected(String key) {
        assertThatThrownBy(() -> store.put(key, in("x"), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists(key)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void open_missing_throwsNoSuchFile() {
        assertThatThrownBy(() -> store.open("A/none.txt")).isInstanceOf(NoSuchFileException.class);
        assertThatThrownBy(() -> store.size("A/none.txt")).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void delete_isIdempotent() throws Exception {
        store.put("A/d.txt", in("1"), 1);
        store.delete("A/d.txt");
        store.delete("A/d.txt");
        assertThat(store.exists("A/d.txt")).isFalse();
    }

    @Test
    void moveToQuarantine_movesUnderQuarantineAndHidesFromList() throws Exception {
        store.put("A/v.txt", in("1"), 1);
        String newKey = store.moveToQuarantine("A/v.txt");

        assertThat(newKey).isEqualTo(".quarantine/A/v.txt");
        assertThat(Files.exists(base.resolve(".quarantine/A/v.txt"))).isTrue();
        assertThat(store.exists("A/v.txt")).isFalse();
        try (Stream<BlobEntry> s = store.list("")) {
            assertThat(s.toList()).isEmpty();
        }
    }

    @Test
    void list_skipsTmpAndQuarantineAndFiltersPrefix() throws Exception {
        store.put("A/1.txt", in("1"), 1);
        store.put("B/2.txt", in("22"), 2);
        Files.createDirectories(base.resolve(".tmp"));
        Files.writeString(base.resolve(".tmp/partial"), "x");

        try (Stream<BlobEntry> s = store.list(null)) {
            assertThat(s.map(BlobEntry::key).sorted().toList()).containsExactly("A/1.txt", "B/2.txt");
        }
        try (Stream<BlobEntry> s = store.list("B")) {
            List<BlobEntry> l = s.toList();
            assertThat(l).hasSize(1);
            assertThat(l.get(0).size()).isEqualTo(2);
            assertThat(l.get(0).lastModified()).isNotNull();
        }
        try (Stream<BlobEntry> s = store.list("NONE")) {
            assertThat(s.toList()).isEmpty();
        }
    }

    @Test
    void usage_reportsDiskSpace() throws Exception {
        StorageUsage u = store.usage();
        assertThat(u.backend()).isEqualTo("LOCAL");
        assertThat(u.totalBytes()).isPositive();
        assertThat(u.usableBytes()).isNotNegative();
        assertThat(u.isBelow(Long.MAX_VALUE)).isTrue();
    }

    private List<Path> tmpFiles() {
        Path tmp = base.resolve(".tmp");
        if (!Files.isDirectory(tmp)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(tmp)) {
            return s.toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
