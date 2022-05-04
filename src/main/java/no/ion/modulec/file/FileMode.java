package no.ion.modulec.file;

import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable representation of the 12 bits octal file mode bits that contains the file permissions
 * (mask 0777) and the special set-user-ID, set-group-ID, and sticky bits (mask 07000).
 */
//@Immutable
public class FileMode {
    public enum Bit {
        RUSR(00400),
        WUSR(00200),
        XUSR(00100),
        RGRP(00040),
        WGRP(00020),
        XGRP(00010),
        ROTH(00004),
        WOTH(00002),
        XOTH(00001),
        SUID(04000),
        SGID(02000),
        SVTX(01000),

        RALL(00111),
        WALL(00222),
        XALL(00444),

        RWXU(00700),
        RWXG(00070),
        RWXO(00007),

        PERM(00777),
        SPEC(07000),
        MASK(07777);

        private final int value;

        Bit(int value) {
            this.value = value;
        }
    }

    private final int mode;

    public static FileMode fromModeInt(int mode) {
        return new FileMode(mode & Bit.MASK.value);
    }

    public static FileMode fromModeBits(Bit... bits) {
        int mode = 0;
        for (Bit bit : bits)
            mode |= bit.value;
        return new FileMode(mode);
    }

    /** Returns a file mode with the given permissions and without set-user-ID, set-group-ID, and sticky bit. */
    public static FileMode fromPosixFilePermissions(Set<PosixFilePermission> permissions) {
        int mode = 0;
        if (permissions.contains(PosixFilePermission.OWNER_READ)) mode |= Bit.RUSR.value;
        if (permissions.contains(PosixFilePermission.OWNER_WRITE)) mode |= Bit.WUSR.value;
        if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) mode |= Bit.XUSR.value;
        if (permissions.contains(PosixFilePermission.GROUP_READ)) mode |= Bit.RGRP.value;
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)) mode |= Bit.WGRP.value;
        if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) mode |= Bit.XGRP.value;
        if (permissions.contains(PosixFilePermission.OTHERS_READ)) mode |= Bit.ROTH.value;
        if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) mode |= Bit.WOTH.value;
        if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) mode |= Bit.XOTH.value;
        return new FileMode(mode);
    }

    private FileMode(int mode) {
        this.mode = mode;
    }

    /** Returns the octal string of the mode, e.g. 01644 or 0750. */
    public String toOctal() { return String.format("0%o", toInt()); }

    /** Returns the 12 file mode bits as an int. */
    public int toInt() { return mode; }

    /** Returns the 9 file permission bits as an int. */
    public int toFilePermissionInt() { return toInt() & Bit.PERM.value; }

    /** Returns the permission bits as a set of {@link PosixFilePermission}. */
    public Set<PosixFilePermission> toPosixFilePermissionSet() {
        var permissions = new HashSet<PosixFilePermission>();
        if ((mode & Bit.RUSR.value) != 0) permissions.add(PosixFilePermission.OWNER_READ);
        if ((mode & Bit.WUSR.value) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & Bit.XUSR.value) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & Bit.RGRP.value) != 0) permissions.add(PosixFilePermission.GROUP_READ);
        if ((mode & Bit.WGRP.value) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & Bit.XGRP.value) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & Bit.ROTH.value) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
        if ((mode & Bit.WOTH.value) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & Bit.XOTH.value) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        return permissions;
    }

    public boolean readableByOwner() { return (mode & Bit.RUSR.value) != 0; }
    public boolean writeableByOwner() { return (mode & Bit.WUSR.value) != 0; }
    public boolean executableByOwner() { return (mode & Bit.XUSR.value) != 0; }
    public boolean readableByGroup() { return (mode & Bit.RGRP.value) != 0; }
    public boolean writeableByGroup() { return (mode & Bit.WGRP.value) != 0; }
    public boolean executableByGroup() { return (mode & Bit.XGRP.value) != 0; }
    public boolean readableByOther() { return (mode & Bit.ROTH.value) != 0; }
    public boolean writeableByOther() { return (mode & Bit.WOTH.value) != 0; }
    public boolean executableByOther() { return (mode & Bit.XOTH.value) != 0; }

    /**
     * Returns true if and only if the mode has the set-user-ID bit, set-group-ID bit, or the sticky bit set (07000 mask).
     * Returns false if and only if the mode can be represented by the permission bits (0777 mask).
     */
    public boolean hasSpecialBits() { return mode != -1 && (mode & Bit.SPEC.value) != 0; }

    public boolean hasSetUserIdBit() { return mode != -1 && (mode & Bit.SUID.value) != 0; }
    public boolean hasSetGroupIdBit() { return mode != -1 && (mode & Bit.SGID.value) != 0; }
    public boolean hasStickyBit() { return mode != -1 && (mode & Bit.SVTX.value) != 0; }

    public boolean has(Bit bit) { return (mode & bit.value) != 0; }

    public boolean hasAllOf(Bit... bits) {
        for (Bit bit : bits) {
            if ((mode & bit.value) == 0)
                return false;
        }
        return true;
    }

    public boolean hasAnyOf(Bit... bits) {
        for (Bit bit : bits) {
            if ((mode & bit.value) != 0)
                return true;
        }
        return false;
    }

    public boolean hasNoneOf(Bit... bits) {
        for (Bit bit : bits) {
            if ((mode & bit.value) != 0)
                return false;
        }
        return true;
    }

    /** Returns a file mode with the given bits set. */
    public FileMode with(Bit... bits) {
        int returnedMode = mode;
        for (Bit bit : bits)
            returnedMode |= bit.value;
        return new FileMode(returnedMode);
    }

    /** Returns a file mode with the given bits cleared. */
    public FileMode without(Bit... bits) {
        int returnedMode = mode;
        for (Bit bit : bits)
            returnedMode &= ~bit.value;
        return new FileMode(returnedMode);
    }

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
