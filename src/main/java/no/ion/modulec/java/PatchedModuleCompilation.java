package no.ion.modulec.java;

import javax.lang.model.SourceVersion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compiles source directories as-if they're part of an existing module.
 */
public class PatchedModuleCompilation {
    private String module = null;
    private final List<Path> sourceDirectories = new ArrayList<>();
    private ModulePath modulePath;
    private Path outputDirectory = null;
    private Path classesOutputDirectory = null;

    public PatchedModuleCompilation() {}

    /** Name of the module to patch. Required. */
    public PatchedModuleCompilation setModuleName(String module) {
        Objects.requireNonNull(module, "module cannot be null");
        if (!SourceVersion.isName(module))
            throw new IllegalArgumentException("Not a valid module name: " + module);
        this.module = module;
        return this;
    }

    public PatchedModuleCompilation addSourceDirectories(List<Path> sourceDirectories) {
        Objects.requireNonNull(sourceDirectories, "sourceDirectories cannot be null");
        this.sourceDirectories.addAll(sourceDirectories);
        return this;
    }

    public PatchedModuleCompilation setModulePath(ModulePath modulePath) {
        this.modulePath = Objects.requireNonNull(modulePath, "modulePath cannot be null");
        return this;
    }

    public PatchedModuleCompilation setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory cannot be null");
        return this;
    }

    public PatchedModuleCompilation setClassesOutputDirectory(Path classesOutputDirectory) {
        this.classesOutputDirectory = Objects.requireNonNull(classesOutputDirectory, "classesOutputDirectory cannot be null");
        return this;
    }
}
