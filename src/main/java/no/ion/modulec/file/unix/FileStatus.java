package no.ion.modulec.file.unix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class FileStatus {
    private static final int S_IFMT = 0170000;
    private static final int S_IFSOCK = 0140000;
    private static final int S_IFLNK = 0120000;
    private static final int S_IFREG = 0100000;
    private static final int S_IFBLK = 0060000;
    private static final int S_IFDIR = 0040000;
    private static final int S_IFCHR = 0020000;
    private static final int S_IFIFO = 0010000;

    private final UnixPath unixPath;
    private final Map<String, Object> attributes;

    public static FileStatus of(Path path, LinkOption... linkOptions) { return of(UnixPath.of(path), linkOptions); }
    public static FileStatus of(UnixPath unixPath, LinkOption... linkOptions) {
        Map<String, Object> attributes = uncheckIO(() -> Files.readAttributes(unixPath.toPath(), "unix:*", linkOptions));
        return new FileStatus(unixPath, attributes);
    }

    public static Optional<FileStatus> ifExists(Path path, LinkOption... linkOptions) { return ifExists(UnixPath.of(path), linkOptions); }
    public static Optional<FileStatus> ifExists(UnixPath unixPath, LinkOption... linkOptions) {
        Map<String, Object> attributes;
        try {
            attributes = Files.readAttributes(unixPath.toPath(), "unix:*", linkOptions);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Optional.of(new FileStatus(unixPath, attributes));
    }

    private FileStatus(UnixPath unixPath, Map<String, Object> attributes) {
        this.unixPath = unixPath;
        this.attributes = attributes;
    }

    public FileType type() { return FileType.fromMode(get("mode", Integer.class)); }
    public FileModeBits mode() { return FileModeBits.fromMode(get("mode", Integer.class)); }

    private <T> T get(String name, Class<T> type) {
        Object value = attributes.get(name);
        Objects.requireNonNull(value, unixPath.asString() + " has no attribute named '" + name + "'");
        return type.cast(value);
    }
}
