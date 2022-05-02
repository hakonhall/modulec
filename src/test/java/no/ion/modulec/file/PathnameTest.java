package no.ion.modulec.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PathnameTest {
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

        assertTrue(dir.readBasicAttributesIfExists(true).isEmpty(), "Temporary directory was not deleted: " + dir);
    }
}