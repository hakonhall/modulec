package no.ion.modulec;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Makes modular JAR from module source.
 *
 * <p>This class can be invoked as a program, or accessed via {@link #make(Options)}.</p>
 *
 * @see <a href="https://github.com/hakonhall/modulec">https://github.com/hakonhall/modulec</a>
 * @author hakonhall
 */
public class ModuleCompiler {

    private final JavaCompiler javaCompiler;
    private final java.util.spi.ToolProvider jarTool;

    public static ModuleCompiler create() {
        JavaCompiler javaCompiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new ModuleCompilerException("error: no java compiler available");
        }

        java.util.spi.ToolProvider jarTool = java.util.spi.ToolProvider.findFirst("jar")
                .orElseThrow(() -> new ModuleCompilerException("error: no jar tool available"));

        return new ModuleCompiler(javaCompiler, jarTool);
    }

    ModuleCompiler(JavaCompiler javaCompiler, java.util.spi.ToolProvider jarTool) {
        this.javaCompiler = javaCompiler;
        this.jarTool = jarTool;
    }

    public static class Options {
        final List<Path> resourceDirectories = new ArrayList<>();
        final List<String> javacArgs = new ArrayList<>();
        FileSystem fileSystem = FileSystems.getDefault();
        Path outputDirectory = null;
        boolean dryRun = false;
        Path jarPath = null;
        String mainClass = null;
        Path manifest = null;
        String path = null;
        ModuleDescriptor.Version version = null;
        Path sourceDirectory = null;
        boolean help = false;

        public Options() {}
        public Options setOutputDirectory(Path path) { this.outputDirectory = path; return this; }
        public Options setDryRun(boolean dryRun) { this.dryRun = dryRun; return this; }
        public Options setFileSystem(FileSystem fileSystem) { this.fileSystem = requireNonNull(fileSystem); return this; }
        public Options setJarPath(Path path) { this.jarPath = path; return this; }
        public Options setMainClass(String mainClass) { this.mainClass = mainClass; return this; }
        public Options setManifestPath(Path path) { this.manifest = path; return this; }
        public Options setPath(String modulePath) { this.path = modulePath; return this; }
        public Options setSourceDirectory(Path path) { this.sourceDirectory = path; return this; }
        public Options setVersion(ModuleDescriptor.Version version) { this.version = version; return this; }
        public Options addResourceDirectories(Path... paths) { Collections.addAll(resourceDirectories, paths); return this; }
        public Options addJavacArguments(String... arguments) { Collections.addAll(javacArgs, arguments); return this; }
        public Options setHelp(boolean help) { this.help = help; return this; }

        public void validate() {
            resourceDirectories.stream().filter(path -> !Files.isDirectory(path)).findAny().ifPresent(resourceDirectory -> {
                throw new ModuleCompilerException("error: missing resource directory: " + resourceDirectory);
            });

            if (manifest != null && !Files.exists(manifest)) {
                throw new ModuleCompilerException("error: missing manifest file: " + manifest);
            }

            if (sourceDirectory == null) {
                throw new ModuleCompilerException("error: no source directory");
            } else if (!Files.exists(sourceDirectory)) {
                throw new ModuleCompilerException("error: source directory does not exist");
            } else if (!Files.exists(sourceDirectory.resolve("module-info.java"))) {
                throw new ModuleCompilerException("error: no module-info.java in source directory");
            }
        }
    }

    public static class SuccessResult {
        private final String compileOutput;
        private String jarOutput = null;

        SuccessResult(String compileOutput) {
            this.compileOutput = compileOutput.isBlank() ? null : compileOutput;
        }

        public Optional<String> diagnostics() {
            if (jarOutput == null) {
                return Optional.ofNullable(compileOutput);
            } else if (compileOutput == null) {
                return Optional.of(jarOutput);
            } else {
                // Output from javac/jar are always newline terminated.
                return Optional.of(compileOutput + jarOutput);
            }
        }

        void addJarOutput(String jarOutput) {
            this.jarOutput = jarOutput.isBlank() ? null : jarOutput;
        }
    }

    /**
     *  Make modular JAR according to options and return non-fatal diagnostics on success (notes, warnings, help text).
     *
     * @throws ModuleCompilerException on failure
     */
    public SuccessResult make(Options options) throws ModuleCompilerException {
        try {
            options.validate();

            var classesPath = options.outputDirectory.resolve("classes");

            SuccessResult result = runJavaCompiler(options, classesPath);

            String moduleName = getModuleName(classesPath);
            return runJarTool(options, moduleName, classesPath, result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected String getModuleName(Path classesPath) {
        var moduleFinder = ModuleFinder.of(classesPath);

        Set<ModuleReference> references = moduleFinder.findAll();
        switch (references.size()) {
            case 0: throw new FindException("No exploded module found: " + classesPath);
            case 1: break;
            default:
                String referencesString = references.stream()
                        .map(reference -> reference.descriptor().name())
                        .collect(Collectors.joining(", "));
                throw new FindException("Found more than one modules in " + classesPath + ": " + referencesString);
        }
        ModuleReference reference = references.iterator().next();

        return reference.descriptor().name();
    }

    private SuccessResult runJavaCompiler(Options options, Path classesPath) throws IOException {
        var javacOptions = new ArrayList<String>();
        if (options.path != null) {
            javacOptions.add("-p");
            javacOptions.add(options.path);
        }
        if (options.version != null) {
            javacOptions.add("--module-version");
            javacOptions.add(options.version.toString());
        }

        javacOptions.add("-d");
        javacOptions.add(classesPath.toString());
        javacOptions.addAll(options.javacArgs);

        List<Path> javacSourceFiles = new ArrayList<>();
        Deque<Path> directoryQueue = new ArrayDeque<>();
        directoryQueue.add(options.sourceDirectory);
        while (!directoryQueue.isEmpty()) {
            Path directory = directoryQueue.removeFirst();
            try (DirectoryStream<Path> dentryPathStream = Files.newDirectoryStream(directory)) {
                for (Path path : dentryPathStream) {
                    if (path.getFileName().toString().endsWith(".java")) {
                        javacSourceFiles.add(path);
                    } else {
                        // Assume path is a directory: if not, NotDirectoryException is thrown which we'll ignore.
                        // Doing it this way avoids a Files.isDirectory() which anyway is answered by NotDirectoryException.
                        directoryQueue.addLast(path);
                    }
                }
            } catch (NotDirectoryException | FileNotFoundException e) {
                // ignore
            }
        }

        if (options.dryRun) {
            String command = "javac " + Stream
                    .concat(javacOptions.stream(), javacSourceFiles.stream().map(Path::toString))
                    .collect(Collectors.joining(" "));
            return new SuccessResult(command + "\n");
        }

        return compile(javacOptions, javacSourceFiles);
    }

    protected SuccessResult compile(List<String> javacOptions, List<Path> javacSourceFiles) throws ModuleCompilerException {
        List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();
        var listener = new DiagnosticListener<JavaFileObject>() {
            @Override
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                diagnostics.add(diagnostic);
            }
        };

        StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromPaths(javacSourceFiles);

        var fileManager = new ForwardingJavaFileManager<>(standardFileManager) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                       FileObject sibling)
                    throws IOException {
                // sibling.getName(): src/module-info.java
                // className: module-info
                return super.getJavaFileForOutput(location, className, kind, sibling);
            }
        };

        var writer = new StringWriter();
        JavaCompiler.CompilationTask task = javaCompiler.getTask(writer, fileManager, listener, javacOptions, null, compilationUnits);

        boolean success = task.call();

        var message = new StringBuilder();

        String writtenString = writer.toString();
        if (!writtenString.isEmpty()) {
            message.append(writtenString);
        }

        int errorCount = 0;
        int warningCount = 0;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            switch (diagnostic.getKind()) {
                case ERROR:
                    ++errorCount;
                    break;
                case WARNING:
                case MANDATORY_WARNING:
                    ++warningCount;
                    break;
                default:
                    // nothing?
            }

            message.append(diagnostic.toString()).append('\n');
        }

        if (errorCount > 0) {
            message.append(errorCount).append(" error").append(errorCount == 1 ? '\n' : "s\n");
        }
        if (warningCount > 0) {
            message.append(warningCount).append(" warning").append(warningCount == 1 ? '\n' : "s\n");
        }

        if (success) {
            return new SuccessResult(message.toString());
        } else {
            throw new ModuleCompilerException(message.toString());
        }
    }

    private SuccessResult runJarTool(Options options, String moduleName, Path classesPath, SuccessResult result) {
        var jarArgs = new ArrayList<String>();
        jarArgs.add("-c");

        Path jarPath = options.jarPath;
        if (jarPath == null) {
            String versionPart = options.version == null ? "" : "-" + options.version.toString();
            String filename = moduleName + versionPart + ".jar";
            jarPath = options.outputDirectory.resolve(filename);
        }
        jarArgs.add("-f");
        jarArgs.add(jarPath.toString());

        if (options.manifest != null) {
            jarArgs.add("-m");
            jarArgs.add(options.manifest.toString());
        }

        if (options.mainClass != null) {
            String mainClass = options.mainClass;
            if (mainClass.startsWith(".")) {
                mainClass = moduleName + options.mainClass;
            }
            jarArgs.add("-e");
            jarArgs.add(mainClass);
        }

        jarArgs.add("-C");
        jarArgs.add(classesPath.toString());
        jarArgs.add(".");

        for (Path resourceDirectory : options.resourceDirectories) {
            jarArgs.add("-C");
            jarArgs.add(resourceDirectory.toString());
            jarArgs.add(".");
        }

        if (options.dryRun) {
            result.addJarOutput("jar " + String.join(" ", jarArgs) + '\n');
            return result;
        }

        StringWriter outputStringWriter = new StringWriter();
        PrintWriter outputPrintWriter = new PrintWriter(outputStringWriter);
        int code = jarTool.run(outputPrintWriter, outputPrintWriter, jarArgs.toArray(String[]::new));

        if (code == 0) {
            result.addJarOutput(outputStringWriter.toString());
            return result;
        } else {
            throw new ModuleCompilerException(outputStringWriter.toString());
        }
    }

    private void ensureSymlinkTo(Options options, Path path, String target) throws IOException {
        try {
            Path destSymlink = Files.readSymbolicLink(path);
            if (destSymlink.toString().equals(target)) {
                return;  // already correct
            }

            Files.delete(path);
        } catch (NoSuchFileException ignored) {
            Files.createDirectories(path.getParent());
        }

        Files.createSymbolicLink(path, options.fileSystem.getPath(target));
    }

    // Below methods are associated with main()

    public static void main(String... args) throws IOException {
        try {
            SuccessResult result = mainApi(args);
            result.diagnostics().ifPresent(System.out::print);
            System.exit(0);
        } catch (ModuleCompilerException e) {
            // We use System.out also for compiler errors, so use out here too.
            String message = e.getMessage();
            if (!message.isEmpty()) {
                System.out.println(message);
            }

            System.exit(1);
        }
    }

    public static SuccessResult mainApi(String... args) throws ModuleCompilerException {
        ModuleCompiler moduleCompiler = ModuleCompiler.create();
        Options options = parseProgramArguments(FileSystems.getDefault(), args);

        if (options.help) {
            return new SuccessResult(getHelpText());
        }

        return moduleCompiler.make(options);
    }

    static String getHelpText() {
        return  "Usage: modulec [OPTION...] SRC [-- JAVACARG...]\n" +
                "Create a modular JAR file from module source in SRC.\n" +
                "\n" +
                "Options:\n" +
                "  [-C RSRC]...            Include each resource directory RSRC.\n" +
                "  -e,--main-class CLASS   Specify the qualified main class.  If CLASS starts\n" +
                "                          with '.' the main class will be MODULE.CLASS.\n" +
                "  -f,--file JARPATH       Write JAR file to JARPATH instead of the default\n" +
                "                          TARGET/MODULE[-VERSION].jar.\n" +
                "  -m,--manifest MANIFEST  Include the manifest information from MANIFEST file.\n" +
                "  -n,--dry-run            Print javac and jar equivalents without execution.\n" +
                "  -o,--output OUTDIR      Output directory for generated files like class files\n" +
                "                          and the JAR file, by default target.\n" +
                "  -p,--path MODULEPATH    The colon-separated module path used for compilation.\n" +
                "  -v,--version VERSION    The module version.\n";

    }

    static Options parseProgramArguments(FileSystem fileSystem, String... args) {
        Options options = new Options();

        options.setFileSystem(fileSystem);
        options.setOutputDirectory(fileSystem.getPath("target"));

        int i = 0;
        for (; i < args.length; ++i) {
            if (isOptionWithArgument(args, i, "-C")) {
                options.addResourceDirectories(fileSystem.getPath(args[++i]));
            } else if (isOptionWithArgument(args, i, "-e", "--main-class")) {
                options.setMainClass(args[++i]);
            } else if (isOptionWithArgument(args, i, "-f", "--file")) {
                options.setJarPath(fileSystem.getPath(args[++i]));
            } else if (isOption(args, i, "-h", "--help")) {
                options.setHelp(true);
                return options; // short-circuit parsing
            } else if (isOptionWithArgument(args, i, "-m", "--manifest")) {
                options.setManifestPath(fileSystem.getPath(args[++i]));
            } else if (isOption(args, i, "-n", "--dry-run")) {
                options.setDryRun(true);
            } else if (isOptionWithArgument(args, i, "-o", "--output")) {
                options.setOutputDirectory(fileSystem.getPath(args[++i]));
            } else if (isOptionWithArgument(args, i, "-p", "--path")) {
                options.setPath(args[++i]);
            } else if (isOptionWithArgument(args, i, "-v", "--version")) {
                ModuleDescriptor.Version version;
                try {
                    version = ModuleDescriptor.Version.parse(args[++i]);
                } catch (IllegalArgumentException e) {
                    throw new ModuleCompilerException("error: invalid version: " + e.getMessage());
                }
                options.setVersion(version);
            } else if (args[i].startsWith("-")) {
                throw new ModuleCompilerException("error: invalid flag: " + args[i]);
            } else {
                break;
            }
        }

        if (i >= args.length) {
            throw new ModuleCompilerException("error: no source directory");
        }

        options.setSourceDirectory(fileSystem.getPath(args[i++]));

        if (i < args.length) {
            if (args[i].equals("--")) {
                ++i;
            } else {
                throw new ModuleCompilerException("error: extraneous argument: " + args[i]);
            }
        }

        options.addJavacArguments(Arrays.copyOfRange(args, i, args.length));

        return options;
    }

    static boolean isOption(String[] args, int i, String... options) {
        return Stream.of(options).anyMatch(option -> option.equals(args[i]));
    }

    static boolean isOptionWithArgument(String[] args, int i, String... options) {
        if (isOption(args, i, options)) {
            if (i + 1 >= args.length) {
                throw new ModuleCompilerException("error: " + args[i] + " requires an argument");
            }

            return true;
        } else {
            return false;
        }
    }
}
