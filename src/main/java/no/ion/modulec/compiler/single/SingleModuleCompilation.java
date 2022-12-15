package no.ion.modulec.compiler.single;

import no.ion.jhms.Argument;
import no.ion.jhms.HybridModuleContainer;
import no.ion.jhms.RootHybridModule;
import no.ion.modulec.ModuleCompilerException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.file.OutputDirectory;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.jar.Jar;
import no.ion.modulec.jar.ModulePackaging;
import no.ion.modulec.jar.PackagingResult;
import no.ion.modulec.modco.ProgramSpec;

import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import static no.ion.modulec.util.Exceptions.uncheckIO;

class SingleModuleCompilation {
    private final Javac javac;
    private final Jar jar;
    private final ModuleCompiler.MakeParams params;

    private OutputDirectory output;
    private CompilationResult sourceCompilationResult;
    private String moduleName;
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
        output.setJarFilename(moduleName + "@" + params.version() + ".jar");
        jarResult = pack(jarPackaging());
        if (params.testSourceDirectory().isPresent()) {
            testSourceCompilationResult = compile(compileTestSourceParams(params.testSourceDirectory().get()));
            testJarResult = pack(testJarPackaging());
            if (!runTests(true))
                throw new ModuleCompilerException("Testing failed");
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
        if (!result.success())
            throw new ModuleCompilerException(result.makeMessage()).setMultiLine(true);
        if (params.verbose()) {
            System.out.printf("compiled %s to %s in %.3fs%n",
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
                                        .setRelease(params.release())
                                        .setVerbose(params.verbose())
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
                                                                     .setVerbose(params.verbose())
                                                                     .setVersion(params.version())
                                                                     .setWarnings(params.warnings());
        params.testModuleInfo().ifPresent(compileParams::setModuleInfo);
        return compileParams;
    }

    private ModulePackaging jarPackaging() {
        ModulePackaging modulePackaging = ModulePackaging.forCreatingJar(output.jarPathname().path())
                                                         .setVerbose(params.verbose());
        params.mainClass().ifPresent(modulePackaging::setMainClass);
        modulePackaging.addDirectoryTree(sourceCompilationResult.destination());
        params.resourceDirectories().stream().map(Pathname::path).forEach(modulePackaging::addDirectoryTree);
        return modulePackaging;
    }

    private ModulePackaging testJarPackaging() {
        output.jarPathname().copyTo(output.testJarPathname(), StandardCopyOption.REPLACE_EXISTING);

        ModulePackaging packaging = ModulePackaging.forUpdatingJar(output.testJarPathname().path())
                                                   .setVerbose(params.verbose());
        packaging.addDirectoryTree(testSourceCompilationResult.destination());
        params.testResourceDirectories().stream().map(Pathname::path).forEach(packaging::addDirectoryTree);
        return packaging;
    }

    private PackagingResult pack(ModulePackaging packaging) {
        PackagingResult result = jar.pack(packaging);
        if (!result.success())
            throw new ModuleCompilerException(result.out()).setMultiLine(true);
        if (params.verbose())
            System.out.printf("packaged %s%n", result.pathname());
        return result;
    }

    /** Returns true on success. */
    private boolean runTests(boolean verbose) {
        try (HybridModuleContainer container = new HybridModuleContainer()) {
            String modulePath = params.modulePath().toColonSeparatedString() + ":" + testJarResult.pathname();
            container.discoverHybridModulesFromModulePath(modulePath);

            String testBooterModule = "no.ion.hybridmodules.test.junit";
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
                if (verbose) {
                    System.out.printf("javahms -p %s -c %s -m %s %s%n",
                                      modulePath,
                                      moduleName,
                                      testBooterModule,
                                      testJarResult.pathname());
                }
                exitCode = testBooter.intCall("runTests", Argument.of(Path.class, testJarResult.pathname().path()));
            } finally {
                Thread.currentThread().setContextClassLoader(savedContext);
            }

            return exitCode == 0;
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

        for (ProgramSpec programSpec : params.programs()) {
            Pathname programPath = programDirectory.resolve(programSpec.filename());
            String mainClass = programSpec.mainClass();

        }
    }
}
