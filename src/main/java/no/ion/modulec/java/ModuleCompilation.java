package no.ion.modulec.java;

import javax.lang.model.SourceVersion;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ModuleCompilation {
    private String module = null;
    private final List<Path> sourceDirectories = new ArrayList<>();
    private ModuleDescriptor.Version version = null;
    private Path outputDirectory = null;
    private Path classesOutputDirectory = null;

    public ModuleCompilation() {}

    /** Name of the module: Otherwise it is found by parsing module-info.java in the source directories. */
    public ModuleCompilation setModuleName(String module) {
        Objects.requireNonNull(module, "module cannot be null");
        if (!SourceVersion.isName(module))
            throw new IllegalArgumentException("Not a valid module name: " + module);
        this.module = module;
        return this;
    }

    public ModuleCompilation addSourceDirectories(List<Path> sourceDirectories) {
        Objects.requireNonNull(sourceDirectories, "sourceDirectories cannot be null");
        this.sourceDirectories.addAll(sourceDirectories);
        return this;
    }

    public ModuleCompilation setVersion(ModuleDescriptor.Version version) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        return this;
    }

    public ModuleCompilation setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory cannot be null");
        return this;
    }

    public ModuleCompilation setClassesOutputDirectory(Path classesOutputDirectory) {
        this.classesOutputDirectory = Objects.requireNonNull(classesOutputDirectory, "classesOutputDirectory cannot be null");
        return this;
    }
}
