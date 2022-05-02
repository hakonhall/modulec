package no.ion.modulec.file;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static no.ion.modulec.util.Exceptions.uncheckIO;

/**
 * A class similar to {@link Path} with basic methods from {@link Files}.
 */
public class Pathname {
    private final Path path;

    public static Pathname of(Path path) { return new Pathname(path); }
    public Pathname(Path path) { this.path = path; }

    public Pathname parent() { return Pathname.of(path.getParent()); }
    public Pathname resolve(Pathname other) { return resolve(other.path); }
    public Pathname resolve(Path other) { return of(path.resolve(other)); }
    public Pathname resolve(String other) { return of(path.resolve(other)); }

    public FileSystem fileSystem() { return path.getFileSystem(); }
    public String filename() { return path.getFileName().toString(); }
    public Path path() { return path; }
    public File file() { return path.toFile(); }
    public String string() { return path.toString(); }
    @Override public String toString() { return string(); }

    public boolean exists() { return readAttributesIfExists(false).isPresent(); }
    public boolean isFile() { return readAttributesIfExists(false).map(BasicAttributes::isFile).orElse(false); }
    public boolean isDirectory() { return readAttributesIfExists(false).map(BasicAttributes::isDirectory).orElse(false); }
    public boolean isSymlink() { return readAttributesIfExists(false).map(BasicAttributes::isSymlink).orElse(false); }
    public boolean isOther() { return readAttributesIfExists(false).map(BasicAttributes::isOther).orElse(false); }

    public BasicAttributes readAttributes(boolean followSymlinks) { return BasicAttributes.of(path, followSymlinks); }
    public Optional<BasicAttributes> readAttributesIfExists(boolean followSymlinks) { return BasicAttributes.ifExists(path, followSymlinks); }

    /** For each entry in this directory, invoke the callback with a Pathname of this.resolve(filename). */
    public Pathname forEachDirectoryEntry(Consumer<Pathname> callback) {
        DirectoryStream<Path> directoryStream = uncheckIO(() -> Files.newDirectoryStream(path));
        try {
            for (Path subpath : directoryStream) {
                callback.accept(Pathname.of(subpath));
            }
        } finally {
            uncheckIO(directoryStream::close);
        }

        return this;
    }

    @FunctionalInterface
    public interface BasicFilterMap<T> {
        /** Return a present value to include it in the list returned from {@link #find(boolean, BasicFilterMap)}. */
        Optional<T> apply(Pathname pathname, BasicAttributes attributes);
    }

    /**
     * For each pathname in the directory tree rooted by this pathname, or just this pathname if it is not a directory,
     * invoke the filterMap with the pathname and its basic attributes.  If the return value is present, it is added
     * to the returned list.
     *
     * <p>The pathname passed to the callback is obtained by resolving this against its relative path.</p>
     */
    public <T> List<T> find(boolean followSymlinks, BasicFilterMap<T> filterMap) {
        BasicAttributes thisAttributes = readAttributes(followSymlinks);
        if (!thisAttributes.isDirectory())
            return filterMap.apply(this, thisAttributes).map(List::of).orElse(List.of());

        var result = new ArrayList<T>();
        filterMap.apply(this, thisAttributes).ifPresent(result::add);

        var dirs = new ArrayList<Pathname>();
        dirs.add(this);
        while (dirs.size() > 0) {
            dirs.remove(dirs.size() - 1)
                .forEachDirectoryEntry(entry -> {
                    BasicAttributes attributes = entry.readAttributes(followSymlinks);
                    filterMap.apply(entry, attributes).ifPresent(result::add);
                    if (attributes.isDirectory())
                        dirs.add(entry);
                });
        }
        return result;
    }

    public Pathname makeDirectory() {
        uncheckIO(() -> Files.createDirectory(path));
        return this;
    }

    public Pathname makeDirectories() {
        uncheckIO(() -> Files.createDirectories(path));
        return this;
    }

    public Pathname makeParentDirectories() {
        parent().makeDirectories();
        return this;
    }

    /** Delete this file.  If a directory, delete all content recursively first. Return the number of files and directories that were deleted. */
    public int deleteRecursively() {
        Optional<BasicAttributes> attributes = readAttributesIfExists(false);
        if (attributes.isEmpty()) return 0;
        if (attributes.get().isDirectory()) {
            final int[] mutableCount = new int[] {0};
            forEachDirectoryEntry(pathname -> mutableCount[0] += pathname.deleteRecursively());
            uncheckIO(() -> Files.delete(path));
            return mutableCount[0] + 1;
        } else {
            uncheckIO(() -> Files.delete(path));
            return 1;
        }
    }

    /** Reads and returns the UTF-8 content of the regular file at this pathname. */
    public String readUtf8() { return uncheckIO(() -> Files.readString(path)); }

    public Optional<String> readUtf8IfExists() {
        return Optional.ofNullable(uncheckIO(() -> Files.readString(path), NoSuchFileException.class));
    }

    /** Writes the string in UTF-8 encoding as the content of the regular file at this pathname. */
    public Pathname writeUtf8(String string, OpenOption... openOptions) {
        uncheckIO(() -> Files.writeString(path, string, StandardCharsets.UTF_8, openOptions));
        return this;
    }

    public Pathname setLastModified(Instant time) {
        FileTime fileTime = FileTime.from(time);
        uncheckIO(() -> Files.setLastModifiedTime(path, fileTime));
        return this;
    }

    public boolean setLastModifiedIfExists(Instant time) {
        return uncheckIO(() -> Files.setLastModifiedTime(path, FileTime.from(time)), NoSuchFileException.class) != null;
    }

    public Pathname readSymlink() {
        Path p = uncheckIO(() -> Files.readSymbolicLink(this.path));
        return Pathname.of(p);
    }

    /** Create a symlink at this path, pointing to the target. */
    public Pathname makeSymlinkTo(Pathname target) { return makeSymlinkTo(target.path()); }
    public Pathname makeSymlinkTo(String target) { return makeSymlinkTo(fileSystem().getPath(target)); }
    public Pathname makeSymlinkTo(Path target) {
        uncheckIO(() -> Files.createSymbolicLink(path, target));
        return this;
    }

    public record TemporaryDirectory(Pathname directory) implements AutoCloseable {
        @Override
        public void close() { directory.deleteRecursively(); }
    }

    /** Create a temporary directory with the given prefix.  May be null. */
    public static TemporaryDirectory makeTemporaryDirectory(String prefix) {
        Path path = uncheckIO(() -> Files.createTempDirectory(prefix));
        return new TemporaryDirectory(Pathname.of(path));
    }

    /** Get the UNIX file status, see stat(2). */
    public FileStatus readStatus(boolean followSymlinks) { return FileStatus.of(path, followSymlinks); }

    /** Get the UNIX file status, see stat(2). */
    public Optional<FileStatus> readStatusIfExists(boolean followSymlinks) { return FileStatus.ifExists(path, followSymlinks); }

    /** Change UNIX permissions, see chmod(2). */
    public Pathname chmod(int mode) {
        Set<PosixFilePermission> set = FilePermissions.fromMode(mode).asSet();
        uncheckIO(() -> Files.setPosixFilePermissions(path, set));
        return this;
    }

    /** Converts a boolean followSymlinks to an array of LinkOption. */
    static LinkOption[] toOpenLinks(boolean followSymlink) {
        return followSymlink ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
    }
}
