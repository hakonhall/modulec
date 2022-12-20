package no.ion.modulec.modco;

import no.ion.modulec.UsageException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.file.Pathname;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Options {

    private final ModuleCompiler.MakeParams params;

    private Options(ModuleCompiler.MakeParams params) {
        this.params = params;
    }

    public ModuleCompiler.MakeParams params() { return params; }

    public static Options parse(FileSystem fileSystem, String... args) {
        String debug = null;
        Pathname out = Pathname.of(fileSystem.getPath("out"));
        String mainClass = null;
        ModulePath modulePath = null;
        boolean testing = true;
        List<ProgramSpec> programs = new ArrayList<>();
        Release release = Release.ofJre();
        Pathname sourceDirectory = null;
        List<Pathname> resourceDirectories = new ArrayList<>();
        Pathname testModuleInfo = null;
        Pathname testSourceDirectory = null;
        List<Pathname> testResourceDirectories = new ArrayList<>();
        boolean verbose = false;
        ModuleDescriptor.Version version = null;
        String warnings = "all";

        var arguments = new ProgramArgumentIterator(fileSystem, args);
        for (; !arguments.atEnd(); arguments.next()) {
            switch (arguments.arg()) {
                case "-g":
                case "--debug":
                    debug = "";
                    continue;
                case "-h":
                case "--help":
                    throw UsageException.fromResource("no/ion/modulec/modco.usage");
                case "-e":
                case "--main-class":
                    mainClass = arguments.getOptionValueString();
                    continue;
                case "-p":
                case "--module-path":
                    modulePath = new ModulePath().addFromColonSeparatedString(fileSystem, arguments.getOptionValueString());
                    continue;
                case "--no-testing":
                    testing = false;
                    continue;
                case "-o":
                case "--output":
                    out = arguments.getOptionValueAsPathname();
                    continue;
                case "-P":
                case "--program":
                    String value = arguments.getOptionValueString();
                    int equalsIndex = value.indexOf('=');
                    if (equalsIndex == -1) {
                        programs.add(new ProgramSpec(value));
                    } else {
                        String filename = value.substring(0, equalsIndex);
                        String programMainClass = value.substring(equalsIndex + 1);
                        if (filename.isEmpty() || filename.equals(".") || filename.equals("..") || filename.indexOf('/') != -1)
                            throw new UserErrorException("Invalid program filename");
                        programs.add(new ProgramSpec(filename, programMainClass));
                    }
                    continue;
                case "-l":
                case "--release":
                    int releaseInt = arguments.getOptionValueInt();
                    try {
                        release = Release.fromFeatureReleaseCounter(releaseInt);
                    } catch (IllegalArgumentException e) {
                        throw new UserErrorException(e.getMessage());
                    }
                    continue;
                case "-r":
                case "--resources":
                    resourceDirectories.add(arguments.getOptionValueAsExistingDirectory());
                    continue;
                case "-s":
                case "--source":
                    sourceDirectory = arguments.getOptionValueAsExistingDirectory();
                    continue;
                case "-I":
                case "--test-module-info":
                    testModuleInfo = arguments.getOptionValueAsPathname();
                    if (!testModuleInfo.filename().equals("module-info.java"))
                        throw new UserErrorException("--test-module-info must specify a module-info.java file");
                    if (!testModuleInfo.isFile())
                        throw new UserErrorException("No such file: " + testModuleInfo);
                    continue;
                case "-R":
                case "--test-resources":
                    testResourceDirectories.add(arguments.getOptionValueAsExistingDirectory());
                    continue;
                case "-t":
                case "--test-source":
                    testSourceDirectory = arguments.getOptionValueAsExistingDirectory();
                    continue;
                case "-b":
                case "--verbose":
                    verbose = true;
                    continue;
                case "-v":
                case "--version":
                    String versionString = arguments.getOptionValueString();
                    try {
                        version = ModuleDescriptor.Version.parse(versionString);
                    } catch (IllegalArgumentException __) {
                        throw new UserErrorException("Invalid module version: '" + versionString + "'");
                    }
                    continue;
                case "-w":
                case "--warnings":
                    warnings = arguments.getOptionValueString();
                    continue;
                default:
                    if (arguments.arg().startsWith("-"))
                        throw new UserErrorException("Unknown option: '" + arguments.arg() + "'");
                    // fall-through to break statement
            }
            break;
        }

        ModuleCompiler.MakeParams params = new ModuleCompiler.MakeParams(fileSystem);

        if (debug != null)
            params.setDebug(debug);

        params.setRelease(release);
        params.setTesting(testing);

        if (mainClass != null) {
            if (mainClass.startsWith(".")) {
                // if only we had the module name at this point...
                if (!release.isName("foo" + mainClass))
                    throw new UserErrorException("Invalid main-class name suffix: '" + mainClass + "'");
            } else if (!release.isName(mainClass))
                throw new UserErrorException("Invalid main-class name: '" + mainClass + "'");
            params.setMainClass(mainClass);
        }

        for (ProgramSpec program : programs) {
            if (program.mainClass().isEmpty()) {
                if (mainClass == null)
                    throw new UserErrorException("No main class specified for program: " + program.filename());
            } else if (program.mainClass().get().startsWith(".")) {
                // if only we had the module name at this point...
                String mainClassToValidate = "foo" + program.mainClass().get();
                if (!release.isName(mainClassToValidate))
                    throw new UserErrorException("The specified main class suffix is an invalid qualified name suffix: '" +
                                                 program.mainClass().get() + "'");
            } else {
                if (!release.isName(program.mainClass().get()))
                    throw new UserErrorException("The specified main class is an invalid qualified name: '" +
                                                 program.mainClass().get() + "'");
            }
        }
        programs.forEach(params::addProgram);

        params.setVerbose(verbose);

        if (version == null)
            throw new UserErrorException("Missing required option '--version'");
        params.setVersion(version);

        params.setWarnings(warnings.isEmpty() ? Optional.empty() : Optional.of(warnings));

        // Maven layout
        if (sourceDirectory == null) {
            Pathname srcMainJava = Pathname.of(fileSystem.getPath("src/main/java"));
            if (srcMainJava.isDirectory()) {
                if (!srcMainJava.resolve("module-info.java").isFile())
                    throw new UserErrorException("Missing module declaration: src/main/java/module-info.java");
                sourceDirectory = srcMainJava;
                if (resourceDirectories.isEmpty()) {
                    Pathname srcMainResources = Pathname.of(fileSystem.getPath("src/main/resources"));
                    if (srcMainResources.isDirectory())
                        resourceDirectories.add(srcMainResources);
                }
                if (testSourceDirectory == null) {
                    Pathname srcTestJava = Pathname.of(fileSystem.getPath("src/test/java"));
                    if (srcTestJava.isDirectory())
                        testSourceDirectory = srcTestJava;
                }
                if (testResourceDirectories.isEmpty()) {
                    Pathname srcTestResources = Pathname.of(fileSystem.getPath("src/test/resources"));
                    if (srcTestResources.isDirectory())
                        testResourceDirectories.add(srcTestResources);
                }
            }
        }

        // Custom layout
        if (sourceDirectory == null) {
            Pathname src = Pathname.of(fileSystem.getPath("src"));
            if (src.isDirectory()) {
                if (src.resolve("module-info.java").isFile()) {
                    sourceDirectory = src;
                } else {
                    throw new UserErrorException("Missing module declaration: src/module-info.java");
                }

                if (testSourceDirectory == null) {
                    Pathname test = Pathname.of(fileSystem.getPath("test"));
                    if (test.isDirectory())
                        testSourceDirectory = test;
                }
            }
        }

        if (sourceDirectory == null)
            throw new UserErrorException("No source directory found: Missing '--source' option");
        params.setSourceDirectory(sourceDirectory);
        resourceDirectories.forEach(params::addResourceDirectory);

        if (testSourceDirectory == null) {
            if (!testResourceDirectories.isEmpty())
                throw new UserErrorException("Test resource directory specified but not test source directory");
        } else {
            params.setTestSourceDirectory(testSourceDirectory);
            testResourceDirectories.forEach(params::addTestResourceDirectory);
        }

        if (out == null)
            out = Pathname.of(fileSystem, "out");
        params.setOut(out);

        if (modulePath != null)
            params.addToModulePath(modulePath);

        if (testModuleInfo != null)
            params.setTestModuleInfo(testModuleInfo);

        return new Options(params);
    }
}
