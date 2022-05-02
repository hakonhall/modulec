package no.ion.modulec.file.unix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.function.Consumer;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class UnixPath {
    private final Path path;

    public static UnixPath of(FileSystem fileSystem, String first, String... rest) { return new UnixPath(fileSystem.getPath(first, rest)); }
    public static UnixPath of(Path path) { return new UnixPath(path); }

    public UnixPath(Path path) { this.path = path; }

    public UnixPath resolve(String other) { return new UnixPath(path.resolve(other)); }
    public UnixPath resolve(Path other) { return new UnixPath(path.resolve(other)); }
    public UnixPath resolve(UnixPath other) { return new UnixPath(path.resolve(other.path)); }

    public String filename() { return path.getFileName().toString(); }
    public Path toPath() { return path; }
    /** Prefer this over toString(). */
    public String asString() { return path.toString(); }
    @Override public String toString() { return asString(); }

    public FileSystem fileSystem() { return path.getFileSystem(); }

    public FileStatus readStatus(LinkOption... linkOptions) { return FileStatus.of(this, linkOptions); }
    public Optional<FileStatus> readStatusIfExists(LinkOption... linkOptions) { return FileStatus.ifExists(this, linkOptions); }

    /**
     * For each filename in this directory, construct a UnixPath by resolving this with the filename and invoke
     * the callback.  If this is not a directory, the callback is not invoked.
     */
    public void forEachDirectoryEntry(Consumer<UnixPath> callback) { forEachDirectoryEntry2(callback); }

    /**
     * Invoke callback for each directory entry.
     *
     * @see #forEachDirectoryEntry(Consumer)
     * @throws UncheckedIOException if the directory does not exist, or other I/O error occurs
     */
    public void forEachDirectoryEntryIfExists(Consumer<UnixPath> callback) {
        forEachDirectoryEntry2(callback, NoSuchFileException.class, NotDirectoryException.class);
    }

    public UnixPath makeParentDirectories() {
        Path parentDirectory = path.getParent();
        uncheckIO(() -> Files.createDirectories(parentDirectory));
        return this;
    }

    public UnixPath mkdir(FilePermissions permissions) {
        var x = permissions.toPosixFilePermissionSet();
        FileAttribute<?> y = PosixFilePermissions.asFileAttribute(x);
        uncheckIO(() -> Files.createDirectory(path, y));
        return this;
    }

    public byte[] readBytes() { return uncheckIO(() -> Files.readAllBytes(path)); }
    public String readUtf8String() { return uncheckIO(() -> Files.readString(path, StandardCharsets.UTF_8)); }

    public UnixPath writeBytes(byte[] bytes, OpenOption... options) {
        uncheckIO(() -> Files.write(path, bytes, options));
        return this;
    }

    public UnixPath writeUtf8String(String content, OpenOption... options) {
        return writeBytes(content.getBytes(StandardCharsets.UTF_8), options);
    }

    /** Delete this file.  If a directory, delete all content recursively first. */
    public int deleteRecursively() {
        Optional<FileStatus> status = readStatusIfExists(LinkOption.NOFOLLOW_LINKS);
        if (status.isEmpty()) return 0;
        final int[] count = new int[] {0};
        if (status.get().type() == FileType.DIRECTORY) {
            forEachDirectoryEntry(pathname -> count[0] += pathname.deleteRecursively());
        }
        uncheckIO(() -> Files.delete(path));
        return count[0] + 1;
    }

    @SafeVarargs
    private void forEachDirectoryEntry2(Consumer<UnixPath> callback, Class<? extends IOException>... ignored) {
        DirectoryStream<Path> stream = uncheckIO(() -> Files.newDirectoryStream(path), ignored);
        if (stream == null) {
            return;
        }

        try {
            stream.forEach(path -> callback.accept(UnixPath.of(path)));
        } finally {
            uncheckIO(stream::close);
        }
    }
}
