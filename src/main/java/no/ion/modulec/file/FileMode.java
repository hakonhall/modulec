package no.ion.modulec.file;

import java.util.Objects;

public class FileMode {
    private static final int MODE_MASK = 07777;
    private static final int S_ISUID = 04000;
    private static final int S_ISGID = 02000;
    private static final int S_ISVTX = 01000;
    private static final int NON_PERMISSION_MASK = S_ISUID | S_ISGID | S_ISVTX;

    private final int mode;

    public static FileMode fromMode(int mode) {
        return new FileMode(mode & MODE_MASK);
    }

    private FileMode(int mode) { this.mode = mode; }

    public String toOctal() { return String.format("0%o", mode); }
    public int toInt() { return mode; }
    public FilePermissions permissions() { return FilePermissions.fromMode(mode); }
    public boolean isPermissionsOnly() { return (mode & NON_PERMISSION_MASK) == 0; }
    public boolean isSetUserId() { return (mode & S_ISUID) != 0; }
    public boolean isSetGroupId() { return (mode & S_ISGID) != 0; }
    public boolean isSticky() { return (mode & S_ISVTX) != 0; }

    @Override
    public String toString() {
        return "FileMode{" +
                "mode=" + mode +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMode fileMode = (FileMode) o;
        return mode == fileMode.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode);
    }
}
