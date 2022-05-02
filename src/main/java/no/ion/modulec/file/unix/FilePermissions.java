package no.ion.modulec.file.unix;

import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

public class FilePermissions {
    private static final int MASK = 0777;

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

    private final int mode;

    public FilePermissions(int mode) {
        this.mode = mode & MASK;
    }

    public static FilePermissions fromMode(int mode) {
        return new FilePermissions(mode);
    }

    public Set<PosixFilePermission> toPosixFilePermissionSet() {
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
        return permissions;
    }
}
