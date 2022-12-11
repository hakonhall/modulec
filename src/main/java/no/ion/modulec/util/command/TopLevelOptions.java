package no.ion.modulec.util.command;

import no.ion.modulec.compiler.ModulePath;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TopLevelOptions {
    private final ModulePath modulePath = new ModulePath();
    private ModuleDescriptor.Version version;
    private Path buildDirectory;
    private String warnings = "all";
    private String debug;

    public TopLevelOptions(FileSystem fileSystem) {
        this.buildDirectory = fileSystem.getPath("target");
    }

    public ModulePath modulePath() { return modulePath; }
    public Optional<ModuleDescriptor.Version> version() { return Optional.ofNullable(version); }
    public Path buildDirectory() { return buildDirectory; }
    public List<String> options() {
        var options = new ArrayList<String>();
        options.add("-Werror");
        options.add("-Xlint:" + warnings);
        if (debug != null) {
            if (debug.isEmpty()) {
                options.add("-g");
            } else {
                options.add("-g:" + debug);
            }
        }
        return options;
    }

    /** Empty for all (-g), "none" for "-g:none", or a comma-separated list L implying "-g:L". */
    public TopLevelOptions setDebug(String debug) {
        Objects.requireNonNull(debug, "debug cannot be null");
        if (!debug.isEmpty() && Set.of("none", "lines", "vars", "source").containsAll(Set.of(debug.split(",", -1)))) {
            throw new ArgumentException("debug must be empty, or a comma-separated list of: none, lines, vars, or source");

        }
        this.debug = debug;
        return this;
    }

    public TopLevelOptions setModulePath(FileSystem fileSystem, String modulePath) {
        this.modulePath.clear().addFromColonSeparatedString(fileSystem, modulePath);
        return this;
    }

    public TopLevelOptions setVersion(ModuleDescriptor.Version version) {
        this.version = version;
        return this;
    }

    public TopLevelOptions setBuildDirectory(Path buildDirectory) {
        this.buildDirectory = buildDirectory;
        return this;
    }

    /** @param warnings is the same as in javac's {@code -Xlint:WARNINGS}, by default "all". */
    public TopLevelOptions setWarnings(String warnings) {
        this.warnings = Objects.requireNonNull(warnings, "warnings cannot be null");
        return this;
    }

    public void validate() {
        if (buildDirectory == null)
            throw new ArgumentException("Missing required --build option");
    }
}
