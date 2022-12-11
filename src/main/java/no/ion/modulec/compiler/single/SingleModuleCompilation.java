package no.ion.modulec.compiler.single;

import no.ion.jhms.Argument;
import no.ion.jhms.HybridModuleContainer;
import no.ion.jhms.RootHybridModule;
import no.ion.modulec.ModuleCompilerException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.file.DestinationDirectory;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.jar.Jar;
import no.ion.modulec.jar.ModulePackaging;
import no.ion.modulec.jar.PackagingResult;

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

    private DestinationDirectory destination;
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
        destination = initialValidation();
        sourceCompilationResult = compile(compileSourceParams());
        moduleName = resolveModuleName();
        destination.setJarFilename(moduleName + "@" + params.version() + ".jar");
        jarResult = pack(jarPackaging());
        if (params.testSourceDirectory() != null) {
            testSourceCompilationResult = compile(compileTestSourceParams());
            testJarResult = pack(testJarPackaging());
            if (!runTests(true))
                throw new ModuleCompilerException("Testing failed");
        }
    }

    private DestinationDirectory initialValidation() {
        Pathname sourceDirectory = params.sourceDirectory();
        if (sourceDirectory == null)
            throw new UserErrorException("No source directory");
        Pathname moduleInfo = sourceDirectory.resolve("module-info.java");
        if (!moduleInfo.isFile())
            throw new ModuleCompilerException("Missing module declaration in source directory: " + moduleInfo);

        if (params.version() == null)
            throw new UserErrorException("Missing module version");

        Pathname destination = params.destination();
        if (destination == null)
            throw new UserErrorException("Missing destination directory");
        return DestinationDirectory.create(destination, DestinationDirectory.Owner.COMPILER_SINGLE);
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
        return new Javac.CompileParams().setSourceDirectory(params.sourceDirectory())
                                        .addModulePathEntriesFrom(params.modulePath())
                                        .setClassDirectory(destination.outputClassDirectory())
                                        .setVerbose(params.verbose())
                                        .setVersion(params.version());
    }

    private String resolveModuleName() {
        Path moduleInfoClassPath = destination.outputClassDirectory().resolve("module-info.class").path();
        InputStream inputStream = uncheckIO(() -> Files.newInputStream(moduleInfoClassPath, StandardOpenOption.READ));
        ModuleDescriptor moduleDescriptor = uncheckIO(() -> ModuleDescriptor.read(inputStream));
        //String moduleName = resolveModuleName(compilation.sourceDirectory(), compilation.release().sourceVersion());
        return moduleDescriptor.name();
    }

    private Javac.CompileParams compileTestSourceParams() {
        return new Javac.CompileParams().setSourceDirectory(params.testSourceDirectory())
                                        .setModuleInfo(params.testModuleInfo())
                                        .addModulePathEntriesFrom(new ModulePath().addFrom(params.modulePath()))
                                        .patchModule(moduleName, jarResult.pathname())
                                        .setClassDirectory(destination.outputTestClassDirectory())
                                        .setVerbose(params.verbose())
                                        .setVersion(params.version());
    }

    private ModulePackaging jarPackaging() {
        ModulePackaging modulePackaging = ModulePackaging.forCreatingJar(destination.jarPathname().path());
        params.mainClass().ifPresent(modulePackaging::setMainClass);
        params.manifest().ifPresent(manifest -> modulePackaging.setManifest(manifest.path()));
        modulePackaging.addDirectoryTree(sourceCompilationResult.destination());
        params.resourceDirectories().stream().map(Pathname::path).forEach(modulePackaging::addDirectoryTree);
        return modulePackaging;
    }

    private ModulePackaging testJarPackaging() {
        destination.jarPathname().copyTo(destination.testJarPathname(), StandardCopyOption.REPLACE_EXISTING);

        ModulePackaging packaging = ModulePackaging.forUpdatingJar(destination.testJarPathname().path());
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

    /** Returns true on success.
     * @param verbose*/
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
}
