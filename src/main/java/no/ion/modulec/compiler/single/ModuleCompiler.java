package no.ion.modulec.compiler.single;

import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.jar.Jar;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ModuleCompiler {
    private final Javac compiler;
    private final Jar jar;

    public ModuleCompiler() {
        this(new Javac(), new Jar());
    }

    ModuleCompiler(Javac compiler, Jar jar) {
        this.compiler = compiler;
        this.jar = jar;
    }

    public static final class MakeParams {

        private final FileSystem fileSystem;

        private Pathname destination = null;
        private ModulePath modulePath = new ModulePath();
        private List<Pathname> resourceDirectories = new ArrayList<>();
        private Pathname sourceDirectory = null;
        private Pathname testModuleInfo = null;
        private List<Pathname> testResourceDirectories = new ArrayList<>();
        private Pathname testSourceDirectory = null;
        private ModuleDescriptor.Version version = null;

        public MakeParams(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }

        public MakeParams setDestination(Pathname destination) {
            this.destination = Objects.requireNonNull(destination, "destination cannot be null");
            return this;
        }

        public MakeParams addToModulePath(ModulePath modulePath) {
            this.modulePath.addFrom(modulePath);
            return this;
        }

        public MakeParams addResourceDirectory(Pathname resourceDirectory) {
            this.resourceDirectories.add(Objects.requireNonNull(resourceDirectory, "resourceDirectory cannot be null"));
            return this;
        }

        public MakeParams setSourceDirectory(Pathname sourceDirectory) {
            this.sourceDirectory = Objects.requireNonNull(sourceDirectory, "sourceDirectory cannot be null");
            return this;
        }

        public MakeParams setTestModuleInfo(Pathname testModuleInfo) {
            if (!testModuleInfo.filename().equals("module-info.java"))
                throw new UserErrorException("Test module declaration must be named module-info.java: " + testModuleInfo);
            this.testModuleInfo = testModuleInfo;
            return this;
        }

        public MakeParams addTestResourceDirectory(Pathname testResourceDirectory) {
            this.testResourceDirectories.add(Objects.requireNonNull(testResourceDirectory, "testResourceDirectory cannot be null"));
            return this;
        }

        public MakeParams setTestSourceDirectory(Pathname testSourceDirectory) {
            this.testSourceDirectory = Objects.requireNonNull(testSourceDirectory, "testSourceDirectory cannot be null");
            return this;
        }

        public MakeParams setVersion(ModuleDescriptor.Version version) {
            this.version = Objects.requireNonNull(version, "version cannot be null");
            return this;
        }

        public Pathname destination() { return destination; }
        public ModulePath modulePath() { return modulePath; }
        public List<Pathname> resourceDirectories() { return resourceDirectories; }
        public Pathname sourceDirectory() { return sourceDirectory; }
        public Pathname testModuleInfo() { return testModuleInfo; }
        public List<Pathname> testResourceDirectories() { return testResourceDirectories; }
        public Pathname testSourceDirectory() { return testSourceDirectory; }
        public ModuleDescriptor.Version version() { return version; }

        // TODO: Wire these to Options
        public boolean verbose() { return true; }
        // jar(1) works as follows:
        //  - With --no-manifest, --manifest and --main-class are ignored.
        //  - Otherwise, with --main-class, --manifest is ignored.
        // Also: test with release().sourceVersion().isName(mainClass)
        public Optional<String> mainClass() { return Optional.empty(); }
        public Optional<Pathname> manifest() { return Optional.empty(); }
    }

    public void make(MakeParams params) {
        SingleModuleCompilation compilation = new SingleModuleCompilation(compiler, jar, params);
        compilation.make();
    }
}
