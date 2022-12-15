package no.ion.modulec.jar;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This class specifies the packaging of a module into a modular JAR.
 */
public class ModulePackaging {
    private final Path jarFile;
    private final String action;
    private boolean verbose = false;
    private ModuleDescriptor.Version version = null;
    private final List<Include> includes = new ArrayList<>();
    /** null means no jar arg and get default manifest, empty mean --no-manifest, and otherwise --manifest FILE. */
    private Optional<Path> manifest = null;
    private String mainClass = null;

    /** The directory of the jarFile must exist. */
    public static ModulePackaging forCreatingJar(Path jarFile) {
        Objects.requireNonNull(jarFile, "jarFile cannot be null");
        return new ModulePackaging(jarFile, "-c");
    }

    /** The directory of the jarFile must exist. */
    public static ModulePackaging forUpdatingJar(Path jarFile) {
        Objects.requireNonNull(jarFile, "jarFile cannot be null");
        return new ModulePackaging(jarFile, "-u");
    }

    private ModulePackaging(Path jarFile, String action) {
        this.jarFile = jarFile;
        this.action = action;
    }

    public ModulePackaging setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public ModulePackaging setVersion(ModuleDescriptor.Version version) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        return this;
    }

    public record Include(Path directory, List<Path> pathsRelativeDirectory) {}

    public ModulePackaging addDirectoryTree(Path directory) {
        return addFiles(directory, List.of(directory.getFileSystem().getPath(".")));
    }

    public ModulePackaging addFiles(Path directory, List<Path> pathsRelativeDirectory) {
        Objects.requireNonNull(directory, "directory cannot be null");
        pathsRelativeDirectory = pathsRelativeDirectory == null || pathsRelativeDirectory.isEmpty() ?
                List.of(directory.getFileSystem().getPath(".")) :
                List.copyOf(pathsRelativeDirectory);
        this.includes.add(new Include(directory, pathsRelativeDirectory));
        return this;
    }

    public ModulePackaging setManifest(Path manifest) {
        this.manifest = Optional.ofNullable(manifest);
        return this;
    }

    public ModulePackaging setMainClass(String mainClass) {
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass cannot be null");
        return this;
    }

    public Path jarFile() { return jarFile; }
    /** One of -c or -u. */
    public String action() { return action; }
    public List<Include> includes() { return List.copyOf(includes); }
    public Optional<Path> manifest() { return manifest; }
    public String mainClass() { return mainClass; }
    public boolean verbose() { return verbose; }
    public ModuleDescriptor.Version version() { return version; }
}
