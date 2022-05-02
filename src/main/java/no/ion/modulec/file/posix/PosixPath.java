package no.ion.modulec.file.posix;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Objects;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class PosixPath {
    private final Path path;

    public static PosixPath of(Path path) { return new PosixPath(path); }
    public static PosixPath of(FileSystem fileSystem, String first, String... more) { return of(fileSystem.getPath(first, more)); }

    public PosixPath resolve(PosixPath other) { return resolve(other.path); }
    public PosixPath resolve(Path other) { return PosixPath.of(path.resolve(other)); }
    public PosixPath resolve(String other) { return PosixPath.of(path.resolve(other)); }

    public Path toPath() { return path; }

    public FileSystem fileSystem() { return path.getFileSystem(); }

    public PosixFileStatus readStatus(LinkOption... linkOptions) { return PosixFileStatus.of(path, linkOptions); }

    public String asString() { return path.toString(); }
    @Override public String toString() { return asString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PosixPath posixPath = (PosixPath) o;
        return path.equals(posixPath.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    private PosixPath(Path path) { this.path = Objects.requireNonNull(path, "path cannot be null"); }

}
