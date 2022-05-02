package no.ion.modulec.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathnameTest {
    private Pathname tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDirPath) {
        tempDir = Pathname.of(tempDirPath);
    }

    @Test
    void basicAttributes() {
        assertTrue(tempDir.exists());
        assertTrue(tempDir.isDirectory());
        assertFalse(tempDir.isFile());

        Pathname file = tempDir.resolve("foo")
                .writeUtf8("foo", StandardOpenOption.CREATE_NEW);
        BasicAttributes attributes = file.readAttributes(true);
        assertTrue(attributes.isFile());
        assertEquals(3, attributes.size());

        tempDir.resolve("bar").writeUtf8("bar");
        var filenames = new HashSet<String>();
        tempDir.forEachDirectoryEntry(p -> filenames.add(p.filename()));
        assertEquals(Set.of("foo", "bar"), filenames);
    }

    @Test
    void unixAttributes() {
        Pathname fooPath = tempDir.resolve("foo")
                .writeUtf8("foo")
                .chmod(0600);
        FileStatus status = fooPath.readStatus(true);
        assertEquals(FileType.REGULAR_FILE, status.type());
        assertEquals(0600, status.mode().toInt());
    }

    @Test
    void symlink() {
        Pathname foo = tempDir.resolve("foo");
        foo.makeSymlinkTo("bar");
        Pathname target = foo.readSymlink();
        assertEquals("bar", target.string());
    }

    @Test
    void recursiveDelete() {
        final Pathname dir;
        try (var tempDir = Pathname.makeTemporaryDirectory(PathnameTest.class.getName())) {
            dir = tempDir.directory();
            dir.resolve("dir/foo")
               .makeParentDirectories()
               .writeUtf8("foo content");
            dir.resolve("bar")
               .writeUtf8("bar content");
        }

        assertTrue(dir.readAttributesIfExists(true).isEmpty(), "Temporary directory was not deleted: " + dir);
    }
}