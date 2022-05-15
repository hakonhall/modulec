package no.ion.modulec.util.command;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ModuleOptions {
    private final TopLevelOptions topLevelOptions;
    private Path destination;
    private Path jarFile;
    private String mainClass;
    private Path manifest;
    private String moduleName;
    private List<Path> sources = new ArrayList<>();
    private List<Path> resources = new ArrayList<>();
    private ModuleDescriptor.Version version;

    public ModuleOptions(TopLevelOptions topLevelOptions) {
        this.topLevelOptions = topLevelOptions;
    }

    public Optional<Path> destination() { return Optional.ofNullable(destination); }
    public Optional<String> mainClass() { return Optional.ofNullable(mainClass); }
    public Optional<Path> jarFile() { return Optional.ofNullable(jarFile); }
    public Optional<Path> manifest() { return Optional.ofNullable(manifest); }
    public Optional<String> moduleName() { return Optional.ofNullable(moduleName); }
    public List<Path> sources() { return sources; }
    public List<Path> resources() { return resources; }
    public Optional<ModuleDescriptor.Version> version() {
        return version == null ?
                topLevelOptions.version() :
                Optional.of(version);
    }

    public ModuleOptions setDestination(Path destination) {
        this.destination = destination;
        return this;
    }

    public ModuleOptions setJarFile(Path jarFile) {
        this.jarFile = jarFile;
        return this;
    }

    public ModuleOptions setMainClass(String mainClass) {
        this.mainClass = mainClass;
        return this;
    }

    public ModuleOptions setManifest(Path manifest) {
        this.manifest = manifest;
        return this;
    }

    public ModuleOptions setModuleName(String moduleName) {
        this.moduleName = moduleName;
        return this;
    }

    public ModuleOptions addSource(Path source) {
        this.sources.add(source);
        return this;
    }

    public ModuleOptions addResource(Path resourceDirectory) {
        this.resources.add(resourceDirectory);
        return this;
    }

    public ModuleOptions setVersion(ModuleDescriptor.Version version) {
        this.version = version;
        return this;
    }

    public ModuleOptions validate() {
        if (sources.isEmpty())
            throw new ArgumentException("Missing --source for module");
        return this;
    }
}
