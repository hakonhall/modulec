package no.ion.modulec.file.unix;

public enum FileType {
    REGULAR_FILE(0100000),
    DIRECTORY(0040000),
    SYMBOLIC_LINK(0120000),
    FIFO(0010000),
    BLOCK_DEVICE(0060000),
    CHAR_DEVICE(0020000),
    SOCKET(0140000);

    private static final int S_IFMT = 0170000;
    private static final int S_IFSOCK = 0140000;
    private static final int S_IFLNK = 0120000;
    private static final int S_IFREG = 0100000;
    private static final int S_IFBLK = 0060000;
    private static final int S_IFDIR = 0040000;
    private static final int S_IFCHR = 0020000;
    private static final int S_IFIFO = 0010000;

    public static FileType fromMode(int mode) {
        switch (mode & S_IFMT) {
            case S_IFSOCK: return SOCKET;
            case S_IFLNK: return SYMBOLIC_LINK;
            case S_IFREG: return REGULAR_FILE;
            case S_IFBLK: return BLOCK_DEVICE;
            case S_IFDIR: return DIRECTORY;
            case S_IFCHR: return CHAR_DEVICE;
            case S_IFIFO: return FIFO;
        }

        throw new IllegalArgumentException("Invalid file status mode: " + mode);
    }

    private final int mode;

    FileType(int mode) {
        this.mode = mode;
    }

    public int toMode() { return mode; }
}
