package no.ion.modulec.file.unix;

public class FileModeBits {
    private static final int FILE_MODE_BITS = 07777;

    private static final int S_ISUID = 04000;
    private static final int S_ISGID = 02000;
    private static final int S_ISVTX = 01000;

    public static FileModeBits fromMode(int mode) {
        return new FileModeBits(mode);
    }

    private final int mode;

    private FileModeBits(int mode) {
        this.mode = mode & FILE_MODE_BITS;
    }

    public FilePermissions permissions() {
        return FilePermissions.fromMode(mode);
    }
}
