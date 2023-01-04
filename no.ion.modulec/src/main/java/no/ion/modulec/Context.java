package no.ion.modulec;

import java.nio.file.FileSystem;

public interface Context {
    FileSystem fileSystem();
    MessageSink log();
}
