package no.ion.modulec.file;

/**
 * A directory that will be recursively deleted when closed.
 */
public interface TemporaryDirectory extends AutoCloseable {
    Pathname directory();

    @Override
    void close();
}
