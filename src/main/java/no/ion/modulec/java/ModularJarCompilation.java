package no.ion.modulec.java;

import javax.lang.model.SourceVersion;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This class defines everything that is specific to a single module, in a possibly multi-module compilation.
 */
public class ModularJarCompilation {
    private String module = null;
    private final List<Path> sourceDirectories = new ArrayList<>();
    private ModulePath modulePath = new ModulePath();
    private ModuleDescriptor.Version version = null;
    private Path outputDirectory = null;
    private Path classesOutputDirectory = null;
    private Path jarPath = null;
    private final List<Path> resourceDirectories = new ArrayList<>();
    /** null means getting the default and auto-generated manifest, empty() means to make a manifest without. */
    private Optional<Path> manifest = null;
    private String mainClass = null;

    public ModularJarCompilation() {}

    /** Explicitly set module name: avoids looking it up in module-info.java in the source directories. */
    public ModularJarCompilation setModuleName(String module) {
        Objects.requireNonNull(module, "module cannot be null");
        if (!SourceVersion.isName(module))
            throw new IllegalArgumentException("Not a valid module name: " + module);
        this.module = module;
        return this;
    }

    public ModularJarCompilation addSourceDirectories(List<Path> sourceDirectories) {
        Objects.requireNonNull(sourceDirectories, "sourceDirectories cannot be null");
        this.sourceDirectories.addAll(sourceDirectories);
        return this;
    }

    public ModularJarCompilation setModulePath(ModulePath modulePath) {
        this.modulePath = Objects.requireNonNull(modulePath, "modulePath cannot be null");
        return this;
    }

    public ModularJarCompilation setVersion(ModuleDescriptor.Version version) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        return this;
    }

    public ModularJarCompilation setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory cannot be null");
        return this;
    }

    public ModularJarCompilation setClassesOutputDirectory(Path classesOutputDirectory) {
        this.classesOutputDirectory = Objects.requireNonNull(classesOutputDirectory, "classesOutputDirectory cannot be null");
        return this;
    }

    public ModularJarCompilation setJarPath(Path jarPath) {
        this.jarPath = Objects.requireNonNull(jarPath, "jarPath cannot be null");
        return this;
    }

    public ModularJarCompilation addResourceFiles(List<Path> resourceDirectories) {
        Objects.requireNonNull(resourceDirectories, "resourceDirectories cannot be null");
        this.resourceDirectories.addAll(resourceDirectories);
        return this;
    }

    /** Include this specific manifest file instead of an auto-generating one, or if null, make a jar without a manifest. */
    public ModularJarCompilation setManifest(Path manifest) {
        // jar(1) works as follows:
        //  - With --no-manifest, --manifest and --main-class are ignored.
        //  - Otherwise, with --main-class, --manifest is ignored.
        this.manifest = Optional.ofNullable(manifest);
        return this;
    }

    public ModularJarCompilation setMainClass(String mainClass) {
        Objects.requireNonNull(mainClass, "mainClass cannot be null");
        if (!SourceVersion.isName(mainClass))
            throw new IllegalArgumentException("Not a valid class name: " + mainClass);
        this.mainClass = mainClass;
        return this;
    }
}
