package no.ion.modulec.util.command;

import javax.lang.model.SourceVersion;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class ProgramArgumentParser {
    private final FileSystem fileSystem;
    private final String[] args;
    private int argi;
    private final Options options = new Options();

    public static Options parse(FileSystem fileSystem, String... args) throws ArgumentException {
        ProgramArgumentParser parser = new ProgramArgumentParser(fileSystem, args);
        parser.parse();
        return parser.options;
    }

    private ProgramArgumentParser(FileSystem fileSystem, String[] args) {
        this.fileSystem = fileSystem;
        this.args = args;
    }

    private void parse() {
        prepareForArgLoop();
        while (hasMoreArguments()) {
            switch (nextArg()) {
                case "-h":
                case "--help":
                    String helpFile = "no/ion/modulec/modulec.usage";
                    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(helpFile);
                    Objects.requireNonNull(inputStream, "No such resource: " + helpFile);
                    byte[] bytes = uncheckIO(inputStream::readAllBytes);
                    String help = new String(bytes, StandardCharsets.UTF_8);
                    throw new ArgumentException(help);
                case "-P":
                case "--module-path-entry":
                    options.topLevelOptions().modulePath().addEntry(nextAsPathArgument());
                    break;
                case "-p":
                case "--module-path":
                    options.topLevelOptions().setModulePath(fileSystem, nextArg());
                    break;
                case "-v":
                case "--version":
                    options.topLevelOptions().setVersion(nextAsVersionArgument());
                    break;
                case "-w":
                case "--work":
                    options.topLevelOptions().setWork(nextAsPathArgument());
                    break;

                case "-m":
                case "--module":
                    ModuleOptions moduleOptions = options.addModuleOptions();
                    parseModuleOptions(moduleOptions);
                    moduleOptions.validate();
                    break;
                case "-n":
                case "--module-name":
                    ModuleOptions moduleOptions2 = options.addModuleOptions();
                    moduleOptions2.setModuleName(nextAsJavaNameArgument());
                    parseModuleOptions(moduleOptions2.validate());
                    break;

                default:
                    if (arg().startsWith("-")) {
                        throw new ArgumentException("Unknown option: " + arg());
                    } else {
                        throw new ArgumentException("Extraneous argument: " + arg());
                    }
            }
        }

        options.topLevelOptions().validate();
    }

    private void parseModuleOptions(ModuleOptions moduleOptions) {
        while (hasMoreArguments()) {
            switch (peekNextArg()) {
                case "-m":
                case "--module":
                case "-n":
                case "--module-name":
                    return;
            }

            switch (nextArg()) {
                case "-d":
                case "--destination":
                    moduleOptions.setDestination(nextAsPathArgument());
                    break;
                case "-f":
                case "--file":
                    moduleOptions.setJarFile(nextAsPathArgument());
                    break;
                case "-e":
                case "--main-class":
                    moduleOptions.setMainClass(nextAsJavaNameArgument());
                    break;
                case "-M":
                case "--manifest":
                    moduleOptions.setManifest(nextAsPathArgument());
                    break;
                case "-s":
                case "--source":
                    moduleOptions.addSource(nextAsPathArgument());
                    break;
                case "-r":
                case "--resource":
                    moduleOptions.addResource(nextAsPathArgument());
                    break;
                case "-v":
                case "--version":
                    moduleOptions.setVersion(nextAsVersionArgument());
                    break;
                default:
                    if (arg().startsWith("-")) {
                        throw new ArgumentException("Unknown option: " + arg());
                    } else {
                        throw new ArgumentException("Extraneous argument: " + arg());
                    }
            }
        }
    }

    private void prepareForArgLoop() { argi = -1; }
    private boolean hasMoreArguments() { return argi + 1 < args.length; }
    private String peekNextArg() { return args[argi + 1]; }
    private String nextArg() { return args[++argi]; }
    private String arg() { return args[argi]; }
    private String previousArg() { return args[argi - 1]; }

    private String nextOptionArgument() {
        if (!hasMoreArguments())
            throw new ArgumentException("Missing argument to " + previousArg());

        return nextArg();
    }

    private ModuleDescriptor.Version nextAsVersionArgument() {
        try {
            return ModuleDescriptor.Version.parse(nextOptionArgument());
        } catch (IllegalArgumentException e) {
            throw new ArgumentException("Invalid version: " + arg());
        }
    }

    private Path nextAsPathArgument() {
        return fileSystem.getPath(nextOptionArgument());
    }

    private String nextAsJavaNameArgument() {
        String name = nextOptionArgument();
        if (!SourceVersion.isName(name))
            throw new ArgumentException("Invalid qualified name: " + name);
        return name;
    }
}
