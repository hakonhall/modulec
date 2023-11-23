package no.ion.modulec.modco;

import no.ion.modulec.UsageException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.file.Pathname;

import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Options {

    private final ModuleCompiler.MakeParams params;

    private Options(ModuleCompiler.MakeParams params) {
        this.params = params;
    }

    public ModuleCompiler.MakeParams params() { return params; }

    public static Options parse(ProgramContext context, String... args) {
        String debug = "";  // Equivalent to javac's -g
        Pathname out = Pathname.of(context.fileSystem().getPath("out"));
        String mainClass = null;
        ModulePath modulePath = null;
        boolean testing = true;
        List<ProgramSpec> programs = new ArrayList<>();
        Release release = Release.ofJre();
        boolean showCommands = false;
        boolean showDebug = false;
        List<Pathname> sourceDirectories = new ArrayList<>();
        List<Pathname> resourceDirectories = new ArrayList<>();
        List<Pathname> testSourceDirectories = new ArrayList<>();
        boolean lookForTestSource = true;
        List<Pathname> testResourceDirectories = new ArrayList<>();
        ModuleDescriptor.Version version = null;
        String warnings = "all";

        var arguments = new ProgramArgumentIterator(context.fileSystem(), args);
        for (; !arguments.atEnd(); arguments.next()) {
            switch (arguments.arg()) {
                case "-g":
                case "--debug":
                    debug = arguments.getOptionValueString();
                    // Empty DEBUG means -g should not be passed to javac, i.e. avoid calling
                    // ModuleCompiler.MakeParams.setDebug().
                    if (debug.isEmpty())
                        debug = null;
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
                    modulePath = new ModulePath().addFromColonSeparatedString(context.fileSystem(), arguments.getOptionValueString());
                    continue;
                case "-N":
                case "--no-test-source":
                    lookForTestSource = false;
                    continue;
                case "-T":
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
                    sourceDirectories.add(arguments.getOptionValueAsExistingSource());
                    continue;
                case "-R":
                case "--test-resources":
                    testResourceDirectories.add(arguments.getOptionValueAsExistingDirectory());
                    continue;
                case "-t":
                case "--test-source":
                    testSourceDirectories.add(arguments.getOptionValueAsExistingSource());
                    continue;
                case "-b":
                case "--verbose":
                    showCommands = true;
                    showDebug = true;
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

        context.showCommands(showCommands);
        context.showDebug(showDebug);
        context.showMilestones(true);
        ModuleCompiler.MakeParams params = new ModuleCompiler.MakeParams(context);

        if (debug != null)
            params.setDebug(debug);

        params.setRelease(release);

        // Verification and normalization of -T/--no-testing, -t/--test-source, and -N/--no-test-source
        if (!lookForTestSource) testing = false;
        if (!lookForTestSource && !testResourceDirectories.isEmpty())
            throw new UserErrorException("-t/--test-source conflicts with -T/--no-test-source");

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

        if (version != null)
            params.setVersion(version);

        params.setWarnings(warnings.isEmpty() ? Optional.empty() : Optional.of(warnings));

        // Maven layout
        if (sourceDirectories.isEmpty()) {
            Pathname srcMainJava = Pathname.of(context.fileSystem().getPath("src/main/java"));
            if (srcMainJava.isDirectory()) {
                if (!srcMainJava.resolve("module-info.java").isFile())
                    throw new UserErrorException("Missing module declaration: src/main/java/module-info.java");
                sourceDirectories.add(srcMainJava);
                if (resourceDirectories.isEmpty()) {
                    Pathname srcMainResources = Pathname.of(context.fileSystem().getPath("src/main/resources"));
                    if (srcMainResources.isDirectory())
                        resourceDirectories.add(srcMainResources);
                }
                if (testSourceDirectories.isEmpty() && lookForTestSource) {
                    Pathname srcTestJava = Pathname.of(context.fileSystem().getPath("src/test/java"));
                    if (srcTestJava.isDirectory())
                        testSourceDirectories.add(srcTestJava);
                }
                if (testResourceDirectories.isEmpty()) {
                    Pathname srcTestResources = Pathname.of(context.fileSystem().getPath("src/test/resources"));
                    if (srcTestResources.isDirectory())
                        testResourceDirectories.add(srcTestResources);
                }
            }
        }

        // Custom layout
        if (sourceDirectories.isEmpty()) {
            Pathname src = Pathname.of(context.fileSystem().getPath("src"));
            if (src.isDirectory()) {
                if (src.resolve("module-info.java").isFile()) {
                    sourceDirectories.add(src);
                } else {
                    throw new UserErrorException("Missing module declaration: src/module-info.java");
                }

                if (testSourceDirectories.isEmpty() && lookForTestSource) {
                    Pathname test = Pathname.of(context.fileSystem().getPath("test"));
                    if (test.isDirectory())
                        testSourceDirectories.add(test);
                }
            }
        }

        if (sourceDirectories.isEmpty())
            throw new UserErrorException("No source directory found: Missing '--source' option");
        params.addSourceDirectories(sourceDirectories);
        resourceDirectories.forEach(params::addResourceDirectory);

        if (testSourceDirectories.isEmpty()) {
            if (lookForTestSource && !testResourceDirectories.isEmpty())
                throw new UserErrorException("Test resource directory specified but not test source directory");
        } else {
            params.setTesting(testing);
            params.addTestSourceDirectories(testSourceDirectories);
            testResourceDirectories.forEach(params::addTestResourceDirectory);
        }

        if (out == null)
            out = Pathname.of(context.fileSystem(), "out");
        params.setOut(out);

        if (modulePath != null)
            params.addToModulePath(modulePath);

        return new Options(params);
    }
}
