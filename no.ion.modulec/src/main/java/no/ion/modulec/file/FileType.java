package no.ion.modulec.file;

public enum FileType {
    REGULAR_FILE(0100000),
    DIRECTORY(0040000),
    SYMBOLIC_LINK(0120000),
    FIFO(0010000),
    BLOCK_DEVICE(0060000),
    CHARACTER_DEVICE(0020000),
    SOCKET(0140000);

    private static final int S_IFMT = 0170000;
    private static final int S_IFSOCK = 0140000;
    private static final int S_IFLNK = 0120000;
    private static final int S_IFREG = 0100000;
    private static final int S_IFBLK = 0060000;
    private static final int S_IFDIR = 0040000;
    private static final int S_IFCHR = 0020000;
    private static final int S_IFIFO = 0010000;

    /** Returns a file type based on the st_mode field of a stat structure, see stat(2). */
    public static FileType fromStatusMode(int statusMode) {
        switch (statusMode & S_IFMT) {
            case S_IFSOCK: return SOCKET;
            case S_IFLNK: return SYMBOLIC_LINK;
            case S_IFREG: return REGULAR_FILE;
            case S_IFBLK: return BLOCK_DEVICE;
            case S_IFDIR: return DIRECTORY;
            case S_IFCHR: return CHARACTER_DEVICE;
            case S_IFIFO: return FIFO;
        }

        throw new IllegalArgumentException("Invalid file status mode: " + statusMode);
    }

    private final int mode;

    FileType(int mode) { this.mode = mode; }

    public boolean isRegularFile() { return this == REGULAR_FILE; }
    public boolean isDirectory() { return this == DIRECTORY; }
    public boolean isSymbolicLink() { return this == SYMBOLIC_LINK; }
    public boolean isFifo() { return this == FIFO; }
    public boolean isSocket() { return this == SOCKET; }
    public boolean isBlockDevice() { return this == BLOCK_DEVICE; }
    public boolean isCharacterDevice() { return this == CHARACTER_DEVICE; }

    /** Returns the file type part of the status mode, see stat(2). */
    public int toStatusMode() { return mode; }
}
