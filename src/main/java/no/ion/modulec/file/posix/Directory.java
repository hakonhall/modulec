package no.ion.modulec.file.posix;

import no.ion.modulec.file.openjdk.OpenJdkFiles;
import no.ion.modulec.file.unix.UnixPath;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Set;

import static no.ion.modulec.util.Exceptions.uncheckIO;

/**
 * An open UNIX directory. Relative path parameters are resolved relative to this directory.
 * {@link #close() Close} this directory when no longer needed to avoid file descriptor leakage.
 */
public class Directory implements AutoCloseable {
    private final UnixPath directory;
    private final SecureDirectoryStream<Path> directoryStream;

    Directory(UnixPath directory, SecureDirectoryStream<Path> directoryStream) {
        this.directory = directory;
        this.directoryStream = directoryStream;
    }

    /** Open a directory at unixPath.  If relative, it is relative this open directory. */
    public Directory openDirectory(UnixPath unixPath, LinkOption... linkOptions) {
        Path filenamePath = directory.resolve(unixPath).toPath();
        return openDirectory(filenamePath, linkOptions);
    }

    public Directory openDirectory(Path path, LinkOption... linkOptions) {
        SecureDirectoryStream<Path> subdirectoryStream = uncheckIO(() -> directoryStream.newDirectoryStream(path, linkOptions));
        return new Directory(directory.resolve(path), subdirectoryStream);
    }

    public Directory openDirectory(String path, LinkOption... linkOptions) {
        Path filenamePath = directory.toPath().getFileSystem().getPath(path);
        return openDirectory(filenamePath, linkOptions);
    }

    public PosixFileAttributes readPosixFileAttributes() {
        var view = getPosixFileAttributesView();
        return uncheckIO(view::readAttributes);
    }

    public PosixFileAttributes readPosixFileAttributes(Path path, LinkOption... linkOptions) {
        var view = getPosixFileAttributesView(path, linkOptions);
        return uncheckIO(view::readAttributes);
    }

    public byte[] readBytes(Path path, Set<OpenOption> openOptions, FileAttribute<?>... fileAttributes) {
        SeekableByteChannel seekableByteChannel = uncheckIO(() -> Files.newByteChannel(path, openOptions, fileAttributes));
        return uncheckIO(() -> OpenJdkFiles.readBytes(seekableByteChannel));
    }

    @Override
    public void close() { uncheckIO(directoryStream::close); }

    private PosixFileAttributeView getPosixFileAttributesView() {
        return directoryStream.getFileAttributeView(PosixFileAttributeView.class);
    }

    private PosixFileAttributeView getPosixFileAttributesView(Path path, LinkOption... linkOptions) {
        return directoryStream.getFileAttributeView(path, PosixFileAttributeView.class, linkOptions);
    }
}
