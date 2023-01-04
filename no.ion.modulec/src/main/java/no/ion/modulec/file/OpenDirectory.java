package no.ion.modulec.file;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static no.ion.modulec.util.Exceptions.uncheckIO;
import static no.ion.modulec.util.Exceptions.uncheckIOIgnoring;

public class OpenDirectory implements DirectoryStream<Pathname>, AutoCloseable {
    private final DirectoryStream<Path> stream;

    public static OpenDirectory open(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        DirectoryStream<Path> directoryStream = uncheckIO(() -> Files.newDirectoryStream(path));
        return new OpenDirectory(directoryStream);
    }

    public static Optional<OpenDirectory> openIfDirectory(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        return uncheckIOIgnoring(() -> Files.newDirectoryStream(path), NoSuchFileException.class, NotDirectoryException.class)
                .map(OpenDirectory::new);
    }

    private OpenDirectory(DirectoryStream<Path> directoryStream) {
        this.stream = directoryStream;
    }

    @Override
    public Iterator<Pathname> iterator() {
        Iterator<Path> iter = stream.iterator();
        return new Iterator<Pathname>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Pathname next() {
                return Pathname.of(iter.next());
            }

            @Override
            public void remove() {
                iter.remove();
            }

            @Override
            public void forEachRemaining(Consumer<? super Pathname> action) {
                iter.forEachRemaining(path -> action.accept(Pathname.of(path)));
            }
        };
    }

    @Override  public void close() { uncheckIO(stream::close); }
}
