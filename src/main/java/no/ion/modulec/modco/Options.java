package no.ion.modulec.modco;

import no.ion.modulec.UsageException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.file.Pathname;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.List;

public class Options {

    private final ModuleCompiler.MakeParams params;

    private Options(ModuleCompiler.MakeParams params) {
        this.params = params;
    }

    public ModuleCompiler.MakeParams params() { return params; }

    public static Options parse(FileSystem fileSystem, String... args) {
        Pathname destination = Pathname.of(fileSystem.getPath("out"));
        ModulePath modulePath = null;
        Pathname sourceDirectory = null;
        List<Pathname> resourceDirectories = new ArrayList<>();
        Pathname testModuleInfo = null;
        Pathname testSourceDirectory = null;
        List<Pathname> testResourceDirectories = new ArrayList<>();
        ModuleDescriptor.Version version = null;

        var arguments = new ProgramArgumentIterator(fileSystem, args);
        for (; !arguments.atEnd(); arguments.next()) {
            switch (arguments.arg()) {
                case "-d":
                case "--destination":
                case "-o":
                case "--output":
                    destination = arguments.getOptionValueAsPathname();
                    continue;
                case "-h":
                case "--help":
                    throw UsageException.fromResource("no/ion/modulec/modco.usage");
                case "-p":
                case "--module-path":
                    modulePath = new ModulePath().addFromColonSeparatedString(fileSystem, arguments.getOptionValueString());
                    continue;
                case "-r":
                case "--resources":
                    resourceDirectories.add(arguments.getOptionValueAsExistingDirectory());
                    continue;
                case "-s":
                case "--source":
                    sourceDirectory = arguments.getOptionValueAsExistingDirectory();
                    continue;
                case "-T":
                case "--test-module-info":
                    testModuleInfo = arguments.getOptionValueAsPathname();
                    continue;
                case "-u":
                case "--test-resources":
                    testResourceDirectories.add(arguments.getOptionValueAsExistingDirectory());
                    continue;
                case "-t":
                case "--test-source":
                    testSourceDirectory = arguments.getOptionValueAsExistingDirectory();
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
                default:
                    if (arguments.arg().startsWith("-"))
                        throw new UserErrorException("Unknown option: '" + arguments.arg() + "'");
                    // fall-through to break statement
            }
            break;
        }

        ModuleCompiler.MakeParams params = new ModuleCompiler.MakeParams(fileSystem);

        if (version == null)
            throw new UserErrorException("Missing required option '--version'");
        params.setVersion(version);

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

        if (destination == null)
            destination = Pathname.of(fileSystem, "out");
        params.setDestination(destination);

        if (modulePath != null)
            params.addToModulePath(modulePath);

        if (testModuleInfo != null) {
            if (!testModuleInfo.isFile())
                throw new UserErrorException("No such file: " + testModuleInfo);
            params.setTestModuleInfo(testModuleInfo);
        }

        return new Options(params);
    }
}
