package no.ion.modulec.util.command;

import no.ion.modulec.java.ModulePath;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TopLevelOptions {
    private final ModulePath modulePath = new ModulePath();
    private ModuleDescriptor.Version version;
    private Path work;
    private String warnings = "all";

    public TopLevelOptions(FileSystem fileSystem) {
        this.work = fileSystem.getPath("target");
    }

    public ModulePath modulePath() { return modulePath; }
    public Optional<ModuleDescriptor.Version> version() { return Optional.ofNullable(version); }
    public Path work() { return work; }
    public List<String> options() { return List.of("-Werror", "-Xlint:" + warnings); }

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

    /** @param warnings is the same as in javac's {@code -Xlint:WARNINGS}, by default "all". */
    public TopLevelOptions setWarnings(String warnings) {
        this.warnings = Objects.requireNonNull(warnings, "detail cannot be null");
        return this;
    }

    public void validate() {
        if (work == null)
            throw new ArgumentException("Missing required --work option");
    }
}
