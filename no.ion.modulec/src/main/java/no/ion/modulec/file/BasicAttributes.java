package no.ion.modulec.file;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Optional;

import static no.ion.modulec.file.Pathname.toOpenLinks;
import static no.ion.modulec.util.Exceptions.uncheckIO;
import static no.ion.modulec.util.Exceptions.uncheckIOIgnoring;

public class BasicAttributes {
    private final BasicFileAttributes attributes;

    public static BasicAttributes of(Path path, boolean followSymlink) {
        var options = toOpenLinks(followSymlink);
        BasicFileAttributes attributes = uncheckIO(() -> Files.readAttributes(path, BasicFileAttributes.class, options));
        return new BasicAttributes(attributes);
    }

    public static Optional<BasicAttributes> ifExists(Path path, boolean followSymlink) {
        return uncheckIOIgnoring(() -> Files.readAttributes(path, BasicFileAttributes.class, toOpenLinks(followSymlink)),
                                 NoSuchFileException.class)
                .map(BasicAttributes::new);
    }

    public boolean isFile() { return attributes.isRegularFile(); }
    public boolean isDirectory() { return attributes.isDirectory(); }
    public boolean isSymlink() { return attributes.isSymbolicLink(); }
    public boolean isOther() { return attributes.isSymbolicLink(); }

    public long size() { return attributes.size(); }
    public Instant lastModified() { return attributes.lastModifiedTime().toInstant(); }

    public Object key() { return attributes.fileKey(); }

    private BasicAttributes(BasicFileAttributes attributes) {
        this.attributes = attributes;
    }
}
