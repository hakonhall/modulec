package no.ion.modulec.file;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static no.ion.modulec.util.Exceptions.uncheckIO;
import static no.ion.modulec.util.Exceptions.uncheckIOIgnoring;

/**
 * A class similar to {@link Path} with basic methods from {@link Files}.
 */
public class Pathname {
    // java.io.tmpdir is guaranteed to exist.
    private static final Pathname TMPDIR = Pathname.of(Paths.get(System.getProperty("java.io.tmpdir")));
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Base-64 encoding with '-' and '_', and '=' suffix filler. */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder();

    private final Path path;

    /** Returns the java.io.tmpdir directory in the default file system. */
    public static Pathname defaultTemporaryDirectory() { return TMPDIR; }

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

    public boolean isEmptyDirectory() {
        Optional<OpenDirectory> directoryOrEmpty = OpenDirectory.openIfDirectory(path);
        if (directoryOrEmpty.isEmpty())
            return false;

        try (OpenDirectory directory = directoryOrEmpty.get()) {
            for (Pathname entry : directory) {
                return false;
            }
        }

        return true;
    }

    public boolean isNonEmptyDirectory() {
        Optional<OpenDirectory> directoryOrEmpty = OpenDirectory.openIfDirectory(path);
        if (directoryOrEmpty.isEmpty())
            return false;

        try (OpenDirectory directory = directoryOrEmpty.get()) {
            for (Pathname entry : directory) {
                return true;
            }
        }

        return false;
    }

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

    public OpenDirectory openDirectory() { return OpenDirectory.open(path); }

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

    /**
     * Creates a new temporary directory in {@code this} directory.
     *
     * <p>The directory name will be {@code prefix+rnd+suffix}, where rnd is a random sequence of
     * letters and digits.</p>
     *
     * @param prefix the prefix of the created directory name
     * @param suffix the suffix of the created directory name
     * @param mode   the file mode
     * @return the temporary directory, recursively deleted if {@link TemporaryDirectory#close() closed}
     */
    public TemporaryDirectory makeTemporaryDirectory(String prefix, String suffix, FileMode mode) {
        Objects.requireNonNull(prefix, "prefix may not be null");
        Objects.requireNonNull(suffix, "suffix may not be null");
        while (true) {
            String name = randomPathSafeName();
            if (prefix.isEmpty() && name.startsWith("-"))
                continue;  // Avoid a directory name starting or ending with '-' or '_'.
            Path tmpdir = path.resolve(prefix + name + suffix);
            var attributes = mode == null ? new FileAttribute<?>[0] : new FileAttribute<?>[] { mode.permissionsAsFileAttribute() };
            if (uncheckIOIgnoring(() -> Files.createDirectory(tmpdir, attributes), FileAlreadyExistsException.class).isEmpty())
                // Try another random name
                continue;
            return new StandardTemporaryDirectory(Pathname.of(tmpdir));
        }
    }

    /**
     * Creates a new temporary directory in the {@link #defaultTemporaryDirectory() default temporary directory}.
     *
     * @see #makeTemporaryDirectory(String, String, FileMode)
     */
    public static TemporaryDirectory makeTmpdir(String prefix, String suffix, FileMode mode) {
        return defaultTemporaryDirectory().makeTemporaryDirectory(prefix, suffix, mode);
    }

    /** A file that will be deleted when closed. */
    public record TemporaryFile(Pathname pathname) implements AutoCloseable {
        @Override
        public void close() { pathname.delete(); }
    }

    /**
     * Creates a new temporary file assuming {@code this} pathname is an existing directory.
     *
     * <p>The filename will be {@code prefix+base64random+suffix}, where base64random is a base-64 encoded
     * random byte sequence.  base64random may contain '-' and '_' characters, but will not start with a '-'.</p>
     *
     * @param prefix prefix of the filename
     * @param suffix suffix of the filename
     * @param mode   the mode to create the file with, or null if the default should be used.
     * @return the pathname of the temporary file, which will be deleted if {@link TemporaryFile#close closed}
     */
    public TemporaryFile makeTemporaryFile(String prefix, String suffix, FileMode mode) {
        Objects.requireNonNull(prefix, "prefix may not be null");
        Objects.requireNonNull(suffix, "suffix may not be null");
        while (true) {
            String name = randomPathSafeName();
            if (prefix.isEmpty() && name.startsWith("-"))
                continue;  // Avoid a filename starting with '-'
            Path file = path.resolve(prefix + name + suffix);
            var attributes = mode == null ? new FileAttribute<?>[0] : new FileAttribute<?>[] { mode.permissionsAsFileAttribute() };
                if (uncheckIOIgnoring(() -> Files.createFile(file, attributes), FileAlreadyExistsException.class).isEmpty())
                    continue;  // Filename clash, try again
            return new TemporaryFile(Pathname.of(file));
        }
    }

    /** @see #makeTemporaryFile(String, String, FileMode) */
    public static TemporaryFile makeTmpfile(String prefix, String suffix, FileMode mode) {
        return TMPDIR.makeTemporaryFile(prefix, suffix, mode);
    }

    /** Returns true if the file or directory was deleted, false if it did not exist. */
    public boolean delete() {
        return uncheckIO(() -> Files.deleteIfExists(path));
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
        return uncheckIOIgnoring(() -> Files.readString(path), NoSuchFileException.class);
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
        return uncheckIOIgnoring(() -> Files.setLastModifiedTime(path, FileTime.from(time)), NoSuchFileException.class) != null;
    }

    public Pathname readSymlink() {
        Path p = uncheckIO(() -> Files.readSymbolicLink(this.path));
        return Pathname.of(p);
    }

    /** Create a symlink at this path with the given content. */
    public Pathname makeSymlinkContaining(Pathname target) { return makeSymlinkContaining(target.path()); }
    /** Create a symlink at this path with the given content. */
    public Pathname makeSymlinkContaining(String target) { return makeSymlinkContaining(fileSystem().getPath(target)); }
    /** Create a symlink at this path with the given content. */
    public Pathname makeSymlinkContaining(Path target) {
        uncheckIO(() -> Files.createSymbolicLink(path, target));
        return this;
    }

    /** Get the UNIX file status, see stat(2). */
    public FileStatus readStatus(boolean followSymlinks) { return FileStatus.of(path, followSymlinks); }

    /** Get the UNIX file status, see stat(2). */
    public Optional<FileStatus> readStatusIfExists(boolean followSymlinks) { return FileStatus.ifExists(path, followSymlinks); }

    /** @see FileStatus#type(). */
    public FileType type(boolean followSymlinks) { return readStatus(followSymlinks).type(); }

    /** @see FileStatus#mode(). */
    public FileMode mode(boolean followSymlinks) { return readStatus(followSymlinks).mode(); }

    /** @see FileStatus#size(). */
    public long size(boolean followSymlinks) { return readStatus(followSymlinks).size(); }

    /** @see FileStatus#uid(). */
    public int uid(boolean followSymlinks) { return readStatus(followSymlinks).uid(); }

    /** @see FileStatus#gid(). */
    public int gid(boolean followSymlinks) { return readStatus(followSymlinks).gid(); }

    /** @see FileStatus#ino(). */
    public long ino(boolean followSymlinks) { return readStatus(followSymlinks).ino(); }

    /** @see FileStatus#dev(). */
    public long dev(boolean followSymlinks) { return readStatus(followSymlinks).dev(); }

    /** @see FileStatus#rdev(). */
    public long rdev(boolean followSymlinks) { return readStatus(followSymlinks).rdev(); }

    /** @see FileStatus#type(). */
    public Optional<String> user(boolean followSymlinks) { return readStatus(followSymlinks).user(); }

    /** @see FileStatus#type(). */
    public Optional<String> group(boolean followSymlinks) { return readStatus(followSymlinks).group(); }

    /** Set the UNIX mode of the file, see chmod(2). */
    public Pathname chmod(int mode) {
        uncheckIO(() -> Files.setAttribute(path, "unix:mode", mode));
        return this;
    }

    /** Sets the UNIX mode of the file, see chmod(2). */
    public Pathname chmod(FileMode mode) { return chmod(mode.toInt()); }

    /** Set the UNIX user and group owners of the file, see chown(2). */
    public Pathname chown(int uid, int gid) {
        // Unfortunately Java invokes either chown(uid, -1) or chown(-1, gid), so this costs double.
        return chown_uid(uid).chown_gid(gid);
    }

    /** Set the UNIX user and group owners of the file without following symlinks, see lchown(2). */
    public Pathname lchown(int uid, int gid) {
        // Unfortunately Java invokes either chown(uid, -1) or chown(-1, gid), so this costs double.
        return lchown_uid(uid).lchown_gid(gid);
    }

    /** Set the user ID owner of the file as-if invoking chown(uid, -1), see chown(2). */
    public Pathname chown_uid(int uid) {
        uncheckIO(() -> Files.setAttribute(path, "unix:uid", uid));
        return this;
    }

    /** Set the user ID owner of the file as-if invoking lchown(uid, -1), see lchown(2). */
    public Pathname lchown_uid(int uid) {
        uncheckIO(() -> Files.setAttribute(path, "unix:uid", uid, LinkOption.NOFOLLOW_LINKS));
        return this;
    }

    /** Set the group ID owner of the file as-if invoking chown(-1, gid), see chown(2). */
    public Pathname chown_gid(int gid) {
        uncheckIO(() -> Files.setAttribute(path, "unix:gid", gid));
        return this;
    }

    /** Set the group ID owner of the file without following symlinks and as-if invoking lchown(-1, gid), see lchown(2). */
    public Pathname lchown_gid(int gid) {
        uncheckIO(() -> Files.setAttribute(path, "unix:gid", gid, LinkOption.NOFOLLOW_LINKS));
        return this;
    }

    /** Converts a boolean followSymlinks to an array of LinkOption. */
    static LinkOption[] toOpenLinks(boolean followSymlink) {
        return followSymlink ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
    }

    private static String randomPathSafeName() {
        // Gives 10k billion unique names
        return randomPathSafeName(8);
    }

    private static String randomPathSafeName(int length) {
        if (length <= 0) return "";
        byte[] bytes = new byte[length];
        var string = new StringBuilder();
        do {
            RANDOM.nextBytes(bytes);
            for (char c : BASE64_ENCODER.encodeToString(bytes).toCharArray()) {
                if (isSafeChar(c)) {
                    string.append(c);
                    if (string.length() >= length) {
                        return string.toString();
                    }
                }
            }
        } while (true);
    }

    private static boolean isSafeChar(char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9');
    }
}
