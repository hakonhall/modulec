package no.ion.modulec.file.posix;

import no.ion.modulec.file.unix.UnixPath;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.Objects;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class PosixFileStatus {
    private final PosixFileAttributes attributes;

    public static PosixFileStatus of(Path path, LinkOption... linkOptions) {
        PosixFileAttributes attributes = uncheckIO(() -> Files.readAttributes(path, PosixFileAttributes.class, linkOptions));
        return new PosixFileStatus(attributes);
    }

    /** See {@link java.nio.file.attribute.PosixFileAttributes#fileKey()}. */
    public Object key() { return Objects.requireNonNull(attributes.fileKey(), "The file key is null"); }

    public boolean isDirectory() { return attributes.isDirectory(); }
    public boolean isRegularFile() { return attributes.isRegularFile(); }
    public boolean isSymbolicLink() { return attributes.isSymbolicLink(); }
    public boolean isOther() { return attributes.isOther(); }

    public long size() { return attributes.size(); }

    public Instant lastModified() { return attributes.lastModifiedTime().toInstant(); }

    public UserPrincipal user() { return attributes.owner(); }
    public GroupPrincipal group() { return attributes.group(); }

    public FilePermissions permissions() { return FilePermissions.fromPosixFilePermissionSet(attributes.permissions()); }

    @Override
    public String toString() {
        return "PosixFileStatus{" +
               "attributes=" + attributes +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PosixFileStatus that = (PosixFileStatus) o;
        return attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }

    private PosixFileStatus(PosixFileAttributes attributes) {
        this.attributes = Objects.requireNonNull(attributes, "attributes cannot be null");
    }
}
