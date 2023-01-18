package no.ion.modulec.compiler.single;

import no.ion.modulec.Context;
import no.ion.modulec.MessageSink;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.jar.Jar;
import no.ion.modulec.modco.ProgramSpec;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ModuleCompiler {
    private final Context context;
    private final Compiler compiler;
    private final Jar jar;

    public ModuleCompiler(Context context) {
        this(context, new Compiler(context), new Jar(context));
    }

    ModuleCompiler(Context context, Compiler compiler, Jar jar) {
        this.context = context;
        this.compiler = compiler;
        this.jar = jar;
    }

    public static final class MakeParams {

        private final Context context;

        private Optional<String> debug = Optional.empty();
        private Pathname out = null;
        private Optional<String> mainClass = Optional.empty();
        private ModulePath modulePath = new ModulePath();
        private List<ProgramSpec> programs = new ArrayList<>();
        private Release release = Release.ofJre();
        private final List<Pathname> resourceDirectories = new ArrayList<>();
        private List<Pathname> sourceDirectories = new ArrayList<>();
        private boolean testing = true;
        private final List<Pathname> testResourceDirectories = new ArrayList<>();
        private List<Pathname> testSourceDirectories = new ArrayList<>();
        private ModuleDescriptor.Version version = null;
        private Optional<String> warnings = Optional.of("all");  // empty => no warnings, "all" => -Xlint, otherwise foo => -Xlint:foo.

        public MakeParams(Context context) {
            this.context = context;
        }

        /** Same as javac's -g:debug.  But "" means -g.  If not called, -g will not be passed to javac. */
        public MakeParams setDebug(String debug) {
            Objects.requireNonNull(debug, "debug cannot be null");
            this.debug = Optional.of(debug);
            return this;
        }

        public MakeParams setOut(Pathname out) {
            this.out = Objects.requireNonNull(out, "out cannot be null");
            return this;
        }

        public MakeParams setMainClass(String mainClass) {
            Objects.requireNonNull(mainClass, "mainClass cannot be null");
            this.mainClass = Optional.of(mainClass);
            return this;
        }

        public MakeParams addToModulePath(ModulePath modulePath) {
            this.modulePath.addFrom(modulePath);
            return this;
        }

        public MakeParams addProgram(ProgramSpec program) {
            this.programs.add(program);
            return this;
        }

        public MakeParams setRelease(Release release) {
            this.release = Objects.requireNonNull(release, "release cannot be null");
            return this;
        }

        public MakeParams addResourceDirectory(Pathname resourceDirectory) {
            this.resourceDirectories.add(Objects.requireNonNull(resourceDirectory, "resourceDirectory cannot be null"));
            return this;
        }

        public MakeParams addSourceDirectories(List<Pathname> sourceDirectories) {
            this.sourceDirectories.addAll(Objects.requireNonNull(sourceDirectories, "sourceDirectories cannot be null"));
            return this;
        }

        public MakeParams setTesting(boolean testing) {
            this.testing = testing;
            return this;
        }

        public MakeParams addTestResourceDirectory(Pathname testResourceDirectory) {
            this.testResourceDirectories.add(Objects.requireNonNull(testResourceDirectory, "testResourceDirectory cannot be null"));
            return this;
        }

        public MakeParams addTestSourceDirectories(List<Pathname> testSourceDirectories) {
            Objects.requireNonNull(testSourceDirectories, "testSourceDirectories cannot be null");
            this.testSourceDirectories.addAll(testSourceDirectories);
            return this;
        }

        public MakeParams setVersion(ModuleDescriptor.Version version) {
            this.version = Objects.requireNonNull(version, "version cannot be null");
            return this;
        }

        public MakeParams setWarnings(Optional<String> warnings) {
            Objects.requireNonNull(warnings, "warnings cannot be null");
            this.warnings = warnings;
            return this;
        }

        public Context context() { return context; }
        public FileSystem fileSystem() { return context.fileSystem(); }
        public MessageSink log() { return context.log(); }

        public Optional<String> debug() { return debug; }
        public Pathname out() { return out; }
        /** May be empty, starting with "." (should prefix module name), or fully qualified. */
        public Optional<String> mainClass() { return mainClass; }
        public ModulePath modulePath() { return modulePath; }
        public List<ProgramSpec> programs() { return List.copyOf(programs); }
        public Release release() { return release; }
        public List<Pathname> resourceDirectories() { return resourceDirectories; }
        public List<Pathname> sourceDirectories() { return sourceDirectories; }
        public boolean testing() { return testing; }
        public List<Pathname> testResourceDirectories() { return testResourceDirectories; }
        public List<Pathname> testSourceDirectories() { return testSourceDirectories; }
        public ModuleDescriptor.Version version() { return version; }
        public Optional<String> warnings() { return warnings; }
    }

    public void make(MakeParams params) {
        SingleModuleCompilation compilation = new SingleModuleCompilation(compiler, jar, params);
        compilation.make();
    }
}
