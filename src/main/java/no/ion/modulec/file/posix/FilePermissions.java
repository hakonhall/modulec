package no.ion.modulec.file.posix;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;

public class FilePermissions {
    private static final int S_ISUID = 04000;
    private static final int S_ISGID = 02000;
    private static final int S_ISVTX = 01000;
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

    private final Set<PosixFilePermission> permissions;

    public static FilePermissions fromPosixFilePermissionSet(Set<PosixFilePermission> permissions) {
        return new FilePermissions(permissions);
    }

    public static FilePermissions fromMode(int mode) {
        var permissions = new HashSet<PosixFilePermission>();
        if ((mode & S_IRUSR) != 0) permissions.add(PosixFilePermission.OWNER_READ);
        if ((mode & S_IWUSR) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & S_IXUSR) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & S_IRGRP) != 0) permissions.add(PosixFilePermission.GROUP_READ);
        if ((mode & S_IWGRP) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & S_IXGRP) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & S_IROTH) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
        if ((mode & S_IWOTH) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & S_IXOTH) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        return new FilePermissions(permissions);
    }

    public int toMode() {
        int mode = 0;
        if (permissions.contains(PosixFilePermission.OWNER_READ))     mode |= S_IRUSR;
        if (permissions.contains(PosixFilePermission.OWNER_WRITE))    mode |= S_IWUSR;
        if (permissions.contains(PosixFilePermission.OWNER_EXECUTE))  mode |= S_IXUSR;
        if (permissions.contains(PosixFilePermission.GROUP_READ))     mode |= S_IRGRP;
        if (permissions.contains(PosixFilePermission.GROUP_WRITE))    mode |= S_IWGRP;
        if (permissions.contains(PosixFilePermission.GROUP_EXECUTE))  mode |= S_IXGRP;
        if (permissions.contains(PosixFilePermission.OTHERS_READ))    mode |= S_IROTH;
        if (permissions.contains(PosixFilePermission.OTHERS_WRITE))   mode |= S_IWOTH;
        if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) mode |= S_IXOTH;
        return mode;
    }

    public String toString() { return PosixFilePermissions.toString(permissions); }

    public Set<PosixFilePermission> toSet() { return Set.copyOf(permissions); }

    private FilePermissions(Set<PosixFilePermission> permissions) {
        this.permissions = permissions;
    }
}
