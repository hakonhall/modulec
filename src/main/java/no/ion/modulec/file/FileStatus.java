package no.ion.modulec.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static no.ion.modulec.file.Pathname.toOpenLinks;
import static no.ion.modulec.util.Exceptions.uncheckIO;

public class FileStatus {
    private final Map<String, Object> attributes;

    public static FileStatus of(Path path, boolean followSymlinks) {
        Map<String, Object> attributes = uncheckIO(() -> Files.readAttributes(path, "unix:*", toOpenLinks(followSymlinks)));
        return new FileStatus(attributes);
    }

    public static Optional<FileStatus> ifExists(Path path, boolean followSymlinks) {
        final Map<String, Object> attributes;
        try {
            attributes = Files.readAttributes(path, "unix:*", toOpenLinks(followSymlinks));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Optional.of(new FileStatus(attributes));
    }

    private FileStatus(Map<String, Object> attributes) {
        this.attributes = Objects.requireNonNull(attributes, "attributes cannot be null");
    }

    public FileType type() { return FileType.fromStatusMode(get("mode", Integer.class)); }
    public FileMode mode() { return FileMode.fromMode(get("mode", Integer.class)); }

    private <T> T get(String name, Class<T> type) {
        Object value = attributes.get(name);
        Objects.requireNonNull(value, "There is no attribute named '" + name + "'");
        return type.cast(value);
    }
}
