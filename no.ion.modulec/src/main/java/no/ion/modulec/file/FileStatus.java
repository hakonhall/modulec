package no.ion.modulec.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
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

    public FileType type() { return FileType.fromStatusMode(getInt("mode")); }
    /** Returns the file mode. */
    public FileMode mode() { return FileMode.fromModeInt(getInt("mode")); }
    /** Returns the size of the file. */
    public long size() { return getLong("size"); }
    /** Returns the user ID of the file owner. */
    public int uid() { return getInt("uid"); }
    /** Returns the group ID of the file owner. */
    public int gid() { return getInt("uid"); }
    /** Returns the inode number. */
    public long ino() { return getLong("ino"); }
    /** Returns the ID of the device containing the file. */
    public long dev() { return getLong("dev"); }
    /** Returns the device ID (if special file). */
    public long rdev() { return getLong("rdev"); }

    /** Returns the username of the uid that owns the file, if there is one associated with the uid. */
    public Optional<String> user() {
        UserPrincipal user = get("owner", UserPrincipal.class);
        return user.getName().equals(Integer.toString(uid())) ?
               Optional.empty() :
               Optional.of(user.getName());
    }

    /** Returns the group name of the gid that owns the file, if there is one associated with the gid. */
    public Optional<String> group() {
        GroupPrincipal group = get("group", GroupPrincipal.class);
        return group.getName().equals(Integer.toString(uid())) ?
               Optional.empty() :
               Optional.of(group.getName());
    }

    private int getInt(String name) { return get(name, Integer.class); }
    private long getLong(String name) { return get(name, Long.class); }

    private <T> T get(String name, Class<T> type) {
        Object value = attributes.get(name);
        Objects.requireNonNull(value, "There is no attribute named '" + name + "'");
        return type.cast(value);
    }
}
