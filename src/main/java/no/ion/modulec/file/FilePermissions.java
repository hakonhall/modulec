package no.ion.modulec.file;

import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class FilePermissions {
    private static final int S_IRWXU = 00700;
    private static final int S_IRUSR = 00400;
    private static final int S_IWUSR = 00200;
    private static final int S_IXUSR = 00100;
    private static final int S_IRWXG = 00070;
    private static final int S_IRGRP = 00040;
    private static final int S_IWGRP = 00020;
    private static final int S_IXGRP = 00010;
    private static final int S_IRWXO = 00007;
    private static final int S_IROTH = 00004;
    private static final int S_IWOTH = 00002;
    private static final int S_IXOTH = 00001;
    private static final int MASK = S_IRWXU | S_IRWXG | S_IRWXO;

    /** Valid or -1. */
    private int mode;

    /** Valid or null. */
    private Set<PosixFilePermission> permissions;

    public static FilePermissions fromMode(int mode) { return new FilePermissions(mode & MASK, null); }
    public static FilePermissions fromSet(Set<PosixFilePermission> permissions) {
        Objects.requireNonNull(permissions, "permissions cannot be null");
        return new FilePermissions(-1, permissions);
    }

    public FilePermissions(int mode, Set<PosixFilePermission> permissions) {
        this.mode = mode;
        this.permissions = permissions;
    }

    public String asOctal() {
        return String.format("0%o", asInt());
    }

    public int asInt() {
        if (mode == -1) {
            mode = 0;
            if (permissions.contains(PosixFilePermission.OWNER_READ)) mode |= S_IRUSR;
            if (permissions.contains(PosixFilePermission.OWNER_WRITE)) mode |= S_IWUSR;
            if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) mode |= S_IXUSR;
            if (permissions.contains(PosixFilePermission.GROUP_READ)) mode |= S_IRGRP;
            if (permissions.contains(PosixFilePermission.GROUP_WRITE)) mode |= S_IWGRP;
            if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) mode |= S_IXGRP;
            if (permissions.contains(PosixFilePermission.OTHERS_READ)) mode |= S_IROTH;
            if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) mode |= S_IWOTH;
            if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) mode |= S_IXOTH;
        }
        return mode;
    }

    public Set<PosixFilePermission> asSet() {
        if (permissions == null) {
            permissions = new HashSet<>();
            if ((mode & S_IRUSR) != 0) permissions.add(PosixFilePermission.OWNER_READ);
            if ((mode & S_IWUSR) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
            if ((mode & S_IXUSR) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
            if ((mode & S_IRGRP) != 0) permissions.add(PosixFilePermission.GROUP_READ);
            if ((mode & S_IWGRP) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
            if ((mode & S_IXGRP) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
            if ((mode & S_IROTH) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
            if ((mode & S_IWOTH) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
            if ((mode & S_IXOTH) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return permissions;
    }

    @Override
    public String toString() {
        return "FilePermissions{" +
                "mode=" + mode +
                ", permissions=" + permissions +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilePermissions that = (FilePermissions) o;
        return mode == that.mode && Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, permissions);
    }
}
