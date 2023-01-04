package no.ion.modulec.file;

public record StandardTemporaryDirectory(Pathname directory) implements TemporaryDirectory {
    @Override
    public void close() {
        directory.deleteRecursively();
    }
}
