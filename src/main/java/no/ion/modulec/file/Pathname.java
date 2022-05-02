package no.ion.modulec.file;

import no.ion.modulec.file.unix.FileStatus;
import no.ion.modulec.file.unix.FileType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static no.ion.modulec.util.Exceptions.uncheckIO;

/**
 * A class similar to {@link Path} with basic methods from {@link Files}.
 */
public class Pathname {
    private final Path path;

    public static Pathname of(Path path) { return new Pathname(path); }

    private Pathname(Path path) { this.path = path; }

    public Path path() { return path; }
    public File file() { return path.toFile(); }
    public String string() { return path.toString(); }
    @Override public String toString() { return string(); }

    public FileSystem fileSystem() { return path.getFileSystem(); }
    public String filename() { return path.getFileName().toString(); }
    public Pathname parent() { return Pathname.of(path.getParent()); }
    public Pathname resolve(Pathname other) { return resolve(other.path); }
    public Pathname resolve(Path other) { return of(path.resolve(other)); }
    public Pathname resolve(String other) { return of(path.resolve(other)); }

    public BasicAttributes readBasicAttributes(boolean followLinks) { return BasicAttributes.of(path, followLinks); }
    public Optional<BasicAttributes> readBasicAttributesIfExists(boolean followLinks) { return BasicAttributes.ofOptional(path, followLinks); }

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
        BasicAttributes thisAttributes = readBasicAttributes(followSymlinks);
        if (!thisAttributes.isDirectory())
            return filterMap.apply(this, thisAttributes).map(List::of).orElse(List.of());

        var result = new ArrayList<T>();
        filterMap.apply(this, thisAttributes).ifPresent(result::add);

        var dirs = new ArrayList<Pathname>();
        dirs.add(this);
        while (dirs.size() > 0) {
            dirs.remove(dirs.size() - 1)
                .forEachDirectoryEntry(entry -> {
                    BasicAttributes attributes = entry.readBasicAttributes(followSymlinks);
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
        Optional<BasicAttributes> attributes = readBasicAttributesIfExists(false);
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

    public record TemporaryDirectory(Pathname directory) implements AutoCloseable {
        @Override
        public void close() { directory.deleteRecursively(); }
    }

    /** Create a temporary directory with the given prefix.  May be null. */
    public static TemporaryDirectory makeTemporaryDirectory(String prefix) {
        Path path = uncheckIO(() -> Files.createTempDirectory(prefix));
        return new TemporaryDirectory(Pathname.of(path));
    }
}
