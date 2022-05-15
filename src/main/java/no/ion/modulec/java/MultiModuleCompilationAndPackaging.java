package no.ion.modulec.java;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * This class specifies multi-module compilation and packaging.
 */
public class MultiModuleCompilationAndPackaging {
    private final Release release;

    private Locale locale = Locale.getDefault();
    private Charset charset = StandardCharsets.UTF_8;
    private final List<ModuleCompilationAndPackaging> modules = new ArrayList<>();
    private ModulePath modulePath = new ModulePath();
    private final List<String> options = new ArrayList<>();
    private Path outputDirectory = null;

    public MultiModuleCompilationAndPackaging(Release release) {
        this.release = Objects.requireNonNull(release, "release cannot be null");
    }

    /** Defaults to the default locale. */
    public MultiModuleCompilationAndPackaging setLocale(Locale locale) {
        this.locale = Objects.requireNonNull(locale, "Locale cannot be null");
        return this;
    }

    /** Defaults to UTF-8. */
    public MultiModuleCompilationAndPackaging setCharset(Charset charset) {
        this.charset = Objects.requireNonNull(charset, "Charset cannot be null");
        return this;
    }

    public ModuleCompilationAndPackaging addModule() {
        var module = new ModuleCompilationAndPackaging(this);
        modules.add(module);
        return module;
    }

    public MultiModuleCompilationAndPackaging setModulePath(ModulePath modulePath) {
        this.modulePath = requireNonNull(modulePath, "modulePath cannot be null");
        return this;
    }

    public MultiModuleCompilationAndPackaging addOptions(String... options) {
        // TODO: verify no options clashes with the ones we control, e.g. --module-source-path, --release, -d, etc.
        Collections.addAll(this.options, options);
        return this;
    }

    /** Set the location of a directory managed by no.ion.modulec to store intermediates across multiple invocations. */
    public MultiModuleCompilationAndPackaging setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = requireNonNull(outputDirectory, "outputDirectory cannot be null");
        return this;
    }

    public Release release() { return release; }
    public Locale locale() { return locale; }
    public Charset charset() { return charset; }
    public List<ModuleCompilationAndPackaging> modules() { return List.copyOf(modules); }
    public ModulePath modulePath() { return modulePath; }
    public List<String> options() { return List.copyOf(options); }
    public Optional<Path> outputDirectory() { return Optional.ofNullable(outputDirectory); }

    @Override
    public String toString() {
        return "MultiModuleCompilationAndPackaging{" +
                "release=" + release +
                ", locale=" + locale +
                ", charset=" + charset +
                ", modules=" + modules +
                ", modulePath=" + modulePath +
                ", options=" + options +
                ", outputDirectory=" + outputDirectory +
                '}';
    }
}
