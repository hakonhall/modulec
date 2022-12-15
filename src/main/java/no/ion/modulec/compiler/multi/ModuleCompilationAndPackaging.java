package no.ion.modulec.compiler.multi;

import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.file.BasicAttributes;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.util.ModuleCompilerException;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * This class specifies the compilation and packing of a single module, within a multi-module compilation.
 */
public class ModuleCompilationAndPackaging {
    private final MultiModuleCompilationAndPackaging parent;

    private String name = null;
    private final List<Path> sourceDirectories = new ArrayList<>();
    private ModulePath modulePath = new ModulePath();
    private ModuleDescriptor.Version version = null;
    private Path classOutputDirectory = null;
    private final List<Resource> resources = new ArrayList<>();
    /** null means JAR includes default manifest, empty means no manifest, otherwise path to manifest. */
    private Optional<Path> manifest = null;
    private String mainClass = null;
    private Path jarPath = null;

    ModuleCompilationAndPackaging(MultiModuleCompilationAndPackaging parent) {
        this.parent = parent;
    }

    public ModuleCompilationAndPackaging setName(String name) {
        if (!parent.release().isName(name))
            throw new ModuleCompilerException("Invalid module name: " + name);
        this.name = name;
        return this;
    }

    public ModuleCompilationAndPackaging addSourceDirectories(List<Path> sourceDirectories) {
        requireNonNull(sourceDirectories, "sourceDirectories cannot be null");
        if (sourceDirectories.isEmpty())
            throw new IllegalArgumentException("There must be at least one source directory");
        this.sourceDirectories.addAll(sourceDirectories);
        return this;
    }

    public ModuleCompilationAndPackaging setModulePath(ModulePath modulePath) {
        this.modulePath = requireNonNull(modulePath, "modulePath cannot be null");
        return this;
    }

    public ModuleCompilationAndPackaging setClassOutputDirectory(Path directory) {
        this.classOutputDirectory = requireNonNull(directory, "directory cannot be null");
        return this;
    }

    public ModuleCompilationAndPackaging setVersion(ModuleDescriptor.Version version) {
        this.version = requireNonNull(version, "version cannot be null");
        return this;
    }

    public record Resource(Path rootDirectory, List<Path> toInclude) {}

    /** Change to directory root and include the paths in toInclude.  A toInclude of null includes all (List.of(".")). */
    public ModuleCompilationAndPackaging addResources(Path rootDirectory, List<Path> toInclude) {
        requireNonNull(rootDirectory, "rootDirectory cannot be null");
        if (toInclude == null)
            toInclude = List.of(rootDirectory.getFileSystem().getPath("."));
        resources.add(new Resource(rootDirectory, toInclude));
        return this;
    }

    public ModuleCompilationAndPackaging addManifest(Path manifest) {
        this.manifest = Optional.ofNullable(manifest);
        return this;
    }

    public ModuleCompilationAndPackaging setMainClass(String mainClass) {
        Objects.requireNonNull(mainClass, "mainClass cannot be null");
        if (!parent.release().isName(mainClass))
            throw new IllegalArgumentException("Not a valid class name: " + mainClass);
        this.mainClass = mainClass;
        return this;
    }

    public ModuleCompilationAndPackaging setJarPath(Path jarPath) {
        this.jarPath = jarPath;
        return this;
    }

    public Optional<String> name() { return Optional.ofNullable(name); }
    public List<Path> sourceDirectories() { return sourceDirectories; }
    public ModulePath modulePath() { return modulePath; }
    public Optional<ModuleDescriptor.Version> version() { return Optional.ofNullable(version); }
    public Optional<Path> classOutputDirectory() { return Optional.ofNullable(classOutputDirectory); }
    public List<Resource> resources() { return resources; }
    public Optional<Path> manifest() { return manifest; }
    public Optional<String> mainClass() { return Optional.ofNullable(mainClass); }
    public Optional<Path> jarPath() { return Optional.ofNullable(jarPath); }

    /** Must be called after compilation. */
    public ModuleCompilationAndPackaging resolveJarFile() {
        if (jarPath == null) {
            // compilation sets this
            jarPath = parent.buildDirectory().orElseThrow().resolve(name).resolve(resolveJarFilename());
        } else {
            Optional<BasicAttributes> attributes = Pathname.of(jarPath).readAttributesIfExists(true);
            if (attributes.isPresent()) {
                if (attributes.get().isDirectory()) {
                    jarPath = jarPath.resolve(resolveJarFilename());
                }
                // jarPath is OK
            } else {
                Optional<BasicAttributes> parentAttributes = Pathname.of(jarPath).parent().readAttributesIfExists(true);
                if (parentAttributes.isEmpty())
                    throw new ModuleCompilerException("Parent directory does not exist: Unable to create JAR file: " + jarPath);
                if (!parentAttributes.get().isDirectory())
                    throw new ModuleCompilerException("Parent of JAR file (" + jarPath + ") not a directory");
                // jarPath is OK
            }
        }

        return this;
    }

    private String resolveJarFilename() {
        return version == null ?
                name + ".jar" :
                name + "-" + version.toString() + ".jar";
    }
}
