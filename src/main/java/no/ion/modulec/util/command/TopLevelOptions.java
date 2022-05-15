package no.ion.modulec.util.command;

import no.ion.modulec.java.ModulePath;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;

public class TopLevelOptions {
    private ModulePath modulePath;
    private ModuleDescriptor.Version version;
    private Path work;

    public TopLevelOptions() {}

    public ModulePath modulePath() { return modulePath; }
    public Optional<ModuleDescriptor.Version> version() { return Optional.ofNullable(version); }
    public Path work() { return work; }

    public TopLevelOptions setModulePath(FileSystem fileSystem, String modulePath) {
        this.modulePath.clear().addFromColonSeparatedString(fileSystem, modulePath);
        return this;
    }

    public TopLevelOptions setVersion(ModuleDescriptor.Version version) {
        this.version = version;
        return this;
    }

    public TopLevelOptions setWork(Path work) {
        this.work = work;
        return this;
    }

    public void validate() {
        if (work == null)
            throw new ArgumentException("Missing required --work option");
    }
}
