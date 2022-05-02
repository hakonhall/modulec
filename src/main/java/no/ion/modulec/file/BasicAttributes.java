package no.ion.modulec.file;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class BasicAttributes {
    private final BasicFileAttributes attributes;

    public static BasicAttributes of(Path path, boolean followSymlink) {
        var options = followSymlink ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
        BasicFileAttributes attributes = uncheckIO(() -> Files.readAttributes(path, BasicFileAttributes.class, options));
        return new BasicAttributes(attributes);
    }

    public static Optional<BasicAttributes> ofOptional(Path path, boolean followSymlink) {
        var options = followSymlink ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
        BasicFileAttributes attributes = uncheckIO(() -> Files.readAttributes(path, BasicFileAttributes.class, options),
                                                   NoSuchFileException.class);
        return Optional.ofNullable(attributes).map(BasicAttributes::new);
    }

    public boolean isFile() { return attributes.isRegularFile(); }
    public boolean isDirectory() { return attributes.isDirectory(); }
    public boolean isSymlink() { return attributes.isSymbolicLink(); }
    public boolean isOther() { return attributes.isSymbolicLink(); }

    public long size() { return attributes.size(); }

    public Object key() { return attributes.fileKey(); }

    private BasicAttributes(BasicFileAttributes attributes) {
        this.attributes = attributes;
    }
}
