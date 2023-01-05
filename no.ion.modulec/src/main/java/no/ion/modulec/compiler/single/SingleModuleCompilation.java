package no.ion.modulec.compiler.single;

import no.ion.jhms.Argument;
import no.ion.jhms.HybridModuleContainer;
import no.ion.jhms.RootHybridModule;
import no.ion.modulec.ModuleCompilerException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.file.FileMode;
import no.ion.modulec.file.OutputDirectory;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.jar.FatJar;
import no.ion.modulec.jar.FatJarSpec;
import no.ion.modulec.jar.HybridModularJarInfo;
import no.ion.modulec.jar.Jar;
import no.ion.modulec.jar.JarInspector;
import no.ion.modulec.jar.ModulePackaging;
import no.ion.modulec.jar.PackagingResult;
import no.ion.modulec.modco.ProgramSpec;
import no.ion.modulec.module.ModuleVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static no.ion.modulec.util.Exceptions.uncheckIO;

class SingleModuleCompilation {
    private static final Pattern JHMS_JAR_REGEX = Pattern.compile("(^|/)no\\.ion\\.jhms-[0-9]+\\.[0-9]+\\.[0-9]+\\.jar$");

    private final Javac javac;
    private final Jar jar;
    private final ModuleCompiler.MakeParams params;

    private OutputDirectory output;
    private CompilationResult sourceCompilationResult;
    private String moduleName;
    private Optional<String> mainClass = null;
    private PackagingResult jarResult;
    private CompilationResult testSourceCompilationResult;
    private PackagingResult testJarResult;

    SingleModuleCompilation(Javac javac, Jar jar, ModuleCompiler.MakeParams params) {
        this.javac = javac;
        this.jar = jar;
        this.params = params;
    }

    void make() {
        output = initialValidation();
        sourceCompilationResult = compile(compileSourceParams());
        moduleName = resolveModuleName();
        mainClass = params.mainClass().map(this::qualifyClass);
        output.setJarFilename(moduleName + "@" + params.version() + ".jar");
        jarResult = pack(jarPackaging());
        if (params.testSourceDirectory().isPresent()) {
            testSourceCompilationResult = compile(compileTestSourceParams(params.testSourceDirectory().get()));
            testJarResult = pack(testJarPackaging());
            if (params.testing())
                runTests();
        }
        makePrograms();
    }

    private OutputDirectory initialValidation() {
        Pathname sourceDirectory = params.sourceDirectory();
        if (sourceDirectory == null)
            throw new UserErrorException("No source directory");
        Pathname moduleInfo = sourceDirectory.resolve("module-info.java");
        if (!moduleInfo.isFile())
            throw new ModuleCompilerException("Missing module declaration in source directory: " + moduleInfo);

        if (params.version() == null)
            throw new UserErrorException("Missing module version");

        Pathname destination = params.out();
        if (destination == null)
            throw new UserErrorException("Missing destination directory");
        return OutputDirectory.create(destination, OutputDirectory.Owner.COMPILER_SINGLE);
    }

    private CompilationResult compile(Javac.CompileParams compileParams) {
        if (compileParams == null) return null;  // propagate abortion of compilation

        CompilationResult result = javac.compile(compileParams);
        String message = result.makeMessage();
        if (!message.isEmpty())
            params.log().info(message);
        if (!result.success())
            throw new ModuleCompilerException(result.makeMessage()).setMultiLine(true).setSilent(true);

        if (result.noop()) {
            params.log().milestone("compiled no source files in %s to %s in %.3fs [up to date]",
                                   compileParams.sourceDirectory(),
                                   result.destination(),
                                   result.duration().toNanos() / 1_000_000_000d);
        } else {
            params.log().milestone("compiled %d source files in %s to %s in %.3fs",
                                   result.sourceFiles(),
                                   compileParams.sourceDirectory(),
                                   result.destination(),
                                   result.duration().toNanos() / 1_000_000_000d);
        }

        return result;
    }

    private Javac.CompileParams compileSourceParams() {
        return new Javac.CompileParams().setDebug(params.debug())
                                        .setSourceDirectory(params.sourceDirectory())
                                        .addModulePathEntriesFrom(params.modulePath())
                                        .setClassDirectory(output.outputClassDirectory())
                                        .setCompilationChecksumFile(output.compilationChecksumFile())
                                        .setRelease(params.release())
                                        .setVersion(params.version())
                                        .setWarnings(params.warnings());
    }

    private String resolveModuleName() {
        Path moduleInfoClassPath = output.outputClassDirectory().resolve("module-info.class").path();
        InputStream inputStream = uncheckIO(() -> Files.newInputStream(moduleInfoClassPath, StandardOpenOption.READ));
        ModuleDescriptor moduleDescriptor = uncheckIO(() -> ModuleDescriptor.read(inputStream));
        //String moduleName = resolveModuleName(compilation.sourceDirectory(), compilation.release().sourceVersion());
        return moduleDescriptor.name();
    }

    private Javac.CompileParams compileTestSourceParams(Pathname testSourceDirectory) {
        Javac.CompileParams compileParams = new Javac.CompileParams().setDebug(params.debug())
                                                                     .setSourceDirectory(testSourceDirectory)
                                                                     .addModulePathEntriesFrom(new ModulePath().addFrom(params.modulePath()))
                                                                     .patchModule(moduleName, jarResult.pathname())
                                                                     .setRelease(params.release())
                                                                     .setClassDirectory(output.outputTestClassDirectory())
                                                                     .setCompilationChecksumFile(output.testCompilationChecksumFile())
                                                                     .setVersion(params.version())
                                                                     .setWarnings(params.warnings());
        params.testModuleInfo().ifPresent(compileParams::setModuleInfo);
        return compileParams;
    }

    private ModulePackaging jarPackaging() {
        ModulePackaging modulePackaging = ModulePackaging.forCreatingJar(output.jarPathname().path());
        mainClass.ifPresent(modulePackaging::setMainClass);
        modulePackaging.addDirectoryTree(sourceCompilationResult.destination());
        params.resourceDirectories().stream().map(Pathname::path).forEach(modulePackaging::addDirectoryTree);
        return modulePackaging;
    }

    private ModulePackaging testJarPackaging() {
        output.jarPathname().copyTo(output.testJarPathname(), StandardCopyOption.REPLACE_EXISTING);

        ModulePackaging packaging = ModulePackaging.forUpdatingJar(output.testJarPathname().path());
        packaging.addDirectoryTree(testSourceCompilationResult.destination());
        params.testResourceDirectories().stream().map(Pathname::path).forEach(packaging::addDirectoryTree);
        return packaging;
    }

    private String qualifyClass(String name) { return name.startsWith(".") ? moduleName + name : name; }

    private PackagingResult pack(ModulePackaging packaging) {
        PackagingResult result = jar.pack(packaging);
        if (!result.success())
            throw new ModuleCompilerException(result.out()).setMultiLine(true);
        params.log().milestone("packaged %s", result.pathname());
        return result;
    }

    private void runTests() {
        try (HybridModuleContainer container = new HybridModuleContainer()) {
            ModulePath modulePath = params.modulePath();
            String modulePathString = modulePath.isEmpty() ?
                                      testJarResult.pathname().toString() :
                                      modulePath.toColonSeparatedString() + ":" + testJarResult.pathname().toString();
            container.discoverHybridModulesFromModulePath(modulePathString);

            String testBooterModule = "no.ion.modulec.junit";
            RootHybridModule testBooter = container.resolve(new HybridModuleContainer
                    .ResolveParams(testBooterModule)
                    .requireVersion("5.9.1"));

            ClassLoader moduleLoader = container.resolve(new HybridModuleContainer.ResolveParams(moduleName)
                                                                                  .requireVersion(params.version()))
                                                .getClassLoader();
            ClassLoader savedContext = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(moduleLoader);
            int exitCode;
            try {
                params.log().command("javahms", "-p", modulePathString, "-c", moduleName, "-m", testBooterModule, testJarResult.pathname().toString());
                exitCode = testBooter.intCall("runTests", Argument.of(Path.class, testJarResult.pathname().path()));
            } finally {
                Thread.currentThread().setContextClassLoader(savedContext);
            }

            if (exitCode != 0)
                throw new ModuleCompilerException("Testing failed");
        }
    }

    private void makePrograms() {
        if (params.programs().isEmpty()) return;

        Pathname programDirectory = output.programDirectory();
        // All programs are slight variation of a file, which we'll make here.
        //  1. The JAR is something very similar to the javahms JAR, which is responsible
        //     for running a hybrid module application.
        //  2. The main module is assumed to be the module we're making.
        //  3. The main module and all transitive modular dependencies must be hybrid modules,
        //     and on the module path, and will be added to the JAR's META-INF/mod directory of
        //     the form MODULE@VERSION.jar.
        //  The resulting JAR will be called the self-contained java wrapper for launching the
        //  hybrid module application, i.e. a self-contained hybrid module application.
        Pathname fatJarPath = output.programJarPath();
        makeFatJar(fatJarPath);

        RandomAccessFile fatJarFile = uncheckIO(() -> new RandomAccessFile(fatJarPath.path().toFile(), "r"));
        FileChannel fatJarChannel = fatJarFile.getChannel();
        long fatJarSize = uncheckIO(fatJarChannel::size);

        for (ProgramSpec programSpec : params.programs()) {
            Pathname programPath = programDirectory.resolve(programSpec.filename());
            String mainClassSpec = programSpec
                    .mainClass()
                    .map(this::qualifyClass)
                    .orElseGet(() -> mainClass
                            .orElseThrow(() -> new ModuleCompilerException(
                                    "No main class specified for program: " + programSpec.filename())));
            String mainClass = mainClassSpec.startsWith(".") ? moduleName + mainClassSpec : mainClassSpec;
            Pathname mainClassPathname = output.outputClassDirectory().resolve(mainClass.replace('.', '/') + ".class");
            if (!mainClassPathname.isFile())
                throw new UserErrorException("No such main class: " + mainClass);
            makeProgram(fatJarChannel, fatJarSize, programPath, mainClass);
        }
    }

    private void makeFatJar(Pathname fatJarPath) {
        // Maps module name and version (MODULE@VERSION) to the pathname of the modular JAR.
        Map<ModuleVersion, HybridModularJarInfo> transitiveJars = new HashMap<>();
        {
            ModulePath effectiveModulePath = new ModulePath().addFrom(params.modulePath());
            effectiveModulePath.addEntry(jarResult.pathname().path());

            Map<ModuleVersion, HybridModularJarInfo> allHybridModules = JarInspector.hybridModulesOf(effectiveModulePath);
            Set<ModuleVersion> unresolved = new HashSet<>();
            unresolved.add(new ModuleVersion(moduleName, params.version()));
            ModuleFinder systemModuleFinder = ModuleFinder.ofSystem();
            while (!unresolved.isEmpty()) {
                Iterator<ModuleVersion> iterator = unresolved.iterator();
                ModuleVersion moduleVersion = iterator.next();
                iterator.remove();

                HybridModularJarInfo info = allHybridModules.get(moduleVersion);
                if (info == null)
                    throw new ModuleCompilerException("Module not found on module path: " + moduleVersion);
                transitiveJars.put(moduleVersion, info);

                Optional<ModuleDescriptor> descriptor = JarInspector.moduleDescriptorOf(info.location());
                if (descriptor.isEmpty())
                    throw new ModuleCompilerException("No module descriptor found: " + info.location());

                for (ModuleDescriptor.Requires requires : descriptor.get().requires()) {
                    if (systemModuleFinder.find(requires.name()).isPresent()) continue;

                    Optional<ModuleDescriptor.Version> compiledVersion = requires.compiledVersion();
                    if (compiledVersion.isEmpty())
                        throw new ModuleCompilerException("Module " + moduleVersion + " requires " + descriptor.get().name() +
                                                          " at an unspecified version");
                    ModuleVersion dependency = new ModuleVersion(requires.name(), compiledVersion.get());
                    if (!transitiveJars.containsKey(dependency))
                        unresolved.add(dependency);
                }
            }
        }

        FatJar fatJar = new FatJar();
        Pathname jhmsJarPathname = jhmsJarPathname(fatJarPath.fileSystem());
        FatJarSpec spec = new FatJarSpec(jhmsJarPathname, fatJarPath);
        spec.addDirectory(FatJar.MODULE_DIRECTORY);
        transitiveJars.values().forEach(info -> spec.addFile(info.location(), pathOfModuleInJar(info)));
        fatJar.extend(spec);
    }

    private static String pathOfModuleInJar(HybridModularJarInfo info) {
        return FatJar.MODULE_DIRECTORY + info.id() + ".jar";
    }

    private Pathname jhmsJarPathname(FileSystem fileSystem) {
        String classPath = System.getProperty("java.class.path");
        String[] entries = classPath.split(":", -1);
        if (entries.length <= 1)
            throw new ModuleCompilerException("Expected more than one entry in the class path: " +
                                              "Will not be able to find no.ion.jhms JAR: " + classPath);
        String jhmsJar = entries[1];
        if (!JHMS_JAR_REGEX.matcher(jhmsJar).find())
            throw new ModuleCompilerException("Expected a no.ion.jhms JAR as the second element of the class path: " +
                                              classPath);
        Pathname jhmsJarPathname = Pathname.of(fileSystem, jhmsJar);
        if (!jhmsJarPathname.isFile())
            throw new ModuleCompilerException("Class path entry not found: " + jhmsJarPathname);

        return jhmsJarPathname;
    }

    private void makeProgram(FileChannel fatJarChannel, long fatJarSize, Pathname programPath, String mainClass) {
        try {
            WritableByteChannel programChannel = Files.newByteChannel(programPath.path(),
                                                                      StandardOpenOption.CREATE,
                                                                      StandardOpenOption.TRUNCATE_EXISTING,
                                                                      StandardOpenOption.WRITE);
            try {
                programChannel.write(ByteBuffer.wrap(utf8Stub(mainClass)));
                fatJarChannel.transferTo(0, fatJarSize, programChannel);
            } finally {
                uncheckIO(programChannel::close);
            }

            // Set executable bits.
            FileMode mode = programPath.readStatus(true).mode();
            FileMode newMode = mode.withExecutable();
            if (!newMode.equals(mode))
                programPath.chmod(newMode);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        params.log().milestone("Wrote " + programPath);
    }

    private byte[] utf8Stub(String mainClass) {
        return """
        #!/bin/bash
        
        if java --version >/dev/null 2>/dev/null; then
          java=java
        elif test -d "$JAVA_HOME"; then
          java="$JAVA_HOME"/bin/java
        else
          echo "No java found in PATH, nor was JAVA_HOME set" >&2
          exit 2
        fi
        
        java_args=()
        while [ "${1:0:2}" == -J ]; do
          java_args+=("${1:2}")
          shift
        done
        
        jhms_args=()
        while [ "${1:0:2}" == -H ]; do
          jhms_args+=("${1:2}")
          shift
        done
        
        exec "$java" -cp "$0" "${java_args[@]}" no.ion.jhms.FatMain "${jhms_args[@]}" %s %s "$@"
        """.formatted(moduleName, mainClass)
           .getBytes(StandardCharsets.UTF_8);
    }
}
