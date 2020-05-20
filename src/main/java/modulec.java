import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class modulec {

    private final FileSystem fileSystem;

    private final List<String> additionalJarDirectoryPaths = new ArrayList<>();
    private final List<String> additionalJavacArgs = new ArrayList<>();
    private Path destPath = null;
    private Path jarPath = null;
    private String mainClass = null;
    private Path manifestPath = null;
    private String modulePath = null;
    private String version = null;
    private Path sourcePath = null;

    static class ModulecException extends RuntimeException {
        public ModulecException(String message) { super(message); }
    }

    public static void main(String... args) throws IOException {
        modulec modulec = new modulec(FileSystems.getDefault());
        try {
            System.exit(modulec.noExitMain(args));
        } catch (ModulecException e) {
            // We use System.out also for compiler errors, so use out here too.
            String message = e.getMessage();
            if (!message.isEmpty()) {
                System.out.println(message);
            }

            System.exit(1);
        }
    }

    public modulec(FileSystem fileSystem) { this.fileSystem = fileSystem; }

    public void help() {
        throw new ModulecException("Usage: modulec [OPTION...] SRC [-- JAVACARG...]\n" +
                "Create a modular JAR file from module source in SRC.\n" +
                "\n" +
                "Options:\n" +
                "  [-C RSRC]...            Include each resource directory RSRC.\n" +
                "  -d DEST                 Directory for generated files like class files, by\n" +
                "                          default target.\n" +
                "  -e,--main-class CLASS   Specify the qualified main class.  If CLASS starts\n" +
                "                          with '.' the main class will be MODULE.CLASS.\n" +
                "  -f,--file JARPATH       Write JAR file to JARPATH instead of the default\n" +
                "                          DEST/MODULE[-VERSION].jar.\n" +
                "  -m,--manifest MANIFEST  Include the manifest information from MANIFEST file.\n" +
                "  -p,--module-path MPATH  The colon-separated module path.\n" +
                "  -v,--version VERSION    The module version.");
    }

    void parseOptions(String... args) {
        destPath = fileSystem.getPath("target");

        int i = 0;
        for (; i < args.length; ++i) {
            if (optionWithArgument(args, i, "-C")) {
                if (!Files.isDirectory(fileSystem.getPath(args[++i]))) {
                    throw new ModulecException("error: " + args[i] + " is not a directory");
                }
                additionalJarDirectoryPaths.add(args[i]);
            } else if (optionWithArgument(args, i, "-d")) {
                destPath = fileSystem.getPath(args[++i]);
            } else if (optionWithArgument(args, i, "-e", "--main-class")) {
                mainClass = args[++i];
            } else if (optionWithArgument(args, i, "-f", "--file")) {
                jarPath = fileSystem.getPath(args[++i]);
            } else if (optionWithArgument(args, i, "-h", "-help")) {
                help();
            } else if (optionWithArgument(args, i, "-m", "--manifest")) {
                manifestPath = fileSystem.getPath(args[++i]);
                if (!Files.exists(manifestPath)) {
                    throw new ModulecException("error: there is no file " + args[i]);
                }
            } else if (optionWithArgument(args, i, "-p", "--module-path")) {
                modulePath = args[++i];
            } else if (optionWithArgument(args, i, "-v", "--version")) {
                version = args[++i];
            } else {
                break;
            }
        }

        if (i >= args.length) {
            throw new ModulecException("error: missing source directory");
        }

        sourcePath = fileSystem.getPath(args[i++]);
        if (!Files.isDirectory(sourcePath)) {
            throw new ModulecException("error: source directory does not exist: " + sourcePath);
        } else if (!Files.isReadable(sourcePath.resolve("module-info.java"))) {
            throw new ModulecException("error: missing module-info.java in source directory");
        }

        if (i < args.length) {
            if (args[i].equals("--")) {
                ++i;
            } else {
                throw new ModulecException("error: extraneous argument: " + args[i]);
            }
        }

        Arrays.stream(args, i, args.length).forEach(additionalJavacArgs::add);
    }

    private static boolean optionWithArgument(String[] args, int i, String... options) {
        if (Stream.of(options).anyMatch(option -> option.equals(args[i]))) {
            if (i + 1 >= args.length) {
                throw new ModulecException("error: " + args[i] + " requires an argument");
            }

            return true;
        } else {
            return false;
        }
    }

    /** As much of main() as possible that can be unit tested. */
    int noExitMain(String... args) throws IOException {
        parseOptions(args);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String moduleName = getModuleName(compiler, sourcePath.resolve("module-info.java"));

        var classesPath = destPath.resolve("classes");

        int compileExitCode = compile(compiler, moduleName, classesPath);
        if (compileExitCode != 0) {
            return compileExitCode;
        }

        return jar(moduleName, classesPath);
    }

    private int compile(JavaCompiler compiler, String moduleName, Path classesPath) throws IOException {
        var javacDestPath = destPath.resolve("javac-classes");
        var moduleSourcePath = destPath.resolve("javac-src");

        Files.createDirectories(classesPath);
        ensureSymlinkTo(javacDestPath.resolve(moduleName), "../classes");
        ensureSymlinkTo(moduleSourcePath.resolve(moduleName), sourcePath.toAbsolutePath().toString());

        var javacArgs = new ArrayList<String>();
        if (modulePath != null) {
            javacArgs.add("-p");
            javacArgs.add(modulePath);
        }
        javacArgs.add("--module-source-path");
        javacArgs.add(moduleSourcePath.toString());
        javacArgs.add("-m");
        javacArgs.add(moduleName);
        if (version != null) {
            javacArgs.add("--module-version");
            javacArgs.add(version);
        }
        javacArgs.add("-d");
        javacArgs.add(javacDestPath.toString());
        javacArgs.addAll(additionalJavacArgs);

        return compiler.run(System.in, System.out, System.out, javacArgs.toArray(String[]::new));
    }

    private int jar(String moduleName, Path classesPath) throws IOException {
        var jarArgs = new ArrayList<String>();
        jarArgs.add("-c");

        if (jarPath == null) {
            String filename = moduleName + (version == null ? "" : "-" + version) + ".jar";
            jarPath = destPath.resolve(filename);
        }
        jarArgs.add("-f");
        jarArgs.add(jarPath.toString());

        if (mainClass != null) {
            if (mainClass.startsWith(".")) {
                mainClass = moduleName + mainClass;
            }
            jarArgs.add("-e");
            jarArgs.add(mainClass);
        }

        jarArgs.add("-C");
        jarArgs.add(classesPath.toString());
        jarArgs.add(".");

        for (String directory : additionalJarDirectoryPaths) {
            jarArgs.add("-C");
            jarArgs.add(directory);
            jarArgs.add(".");
        }

        var jarToolProvider = java.util.spi.ToolProvider.findFirst("jar")
                .orElseThrow(() -> new ModulecException("error: failed to find jar tool"));

        return jarToolProvider.run(System.out, System.out, jarArgs.toArray(String[]::new));
    }

    private void ensureSymlinkTo(Path path, String target) throws IOException {
        try {
            Path destSymlink = Files.readSymbolicLink(path);
            if (destSymlink.toString().equals(target)) {
                return;  // already correct
            }

            Files.delete(path);
        } catch (NoSuchFileException ignored) {
            Files.createDirectories(path.getParent());
        }

        Files.createSymbolicLink(path, fileSystem.getPath(target));
    }

    static String getModuleName(JavaCompiler compiler, Path moduleInfoPath) throws IOException {
        List<String> moduleNames = new ArrayList<>();

        try(StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(moduleInfoPath);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, compilationUnits);
            JavacTask javacTask = (JavacTask) task;
            Iterable<? extends CompilationUnitTree> units = javacTask.parse();

            units.forEach(compilationUnitTree -> {
                List<? extends Tree> typeDeclarations = compilationUnitTree.getTypeDecls();
                for (Tree tree : typeDeclarations) {
                    if (tree instanceof ModuleTree) {
                        ModuleTree moduleTree = (ModuleTree) tree;
                        ExpressionTree name = moduleTree.getName();
                        moduleNames.add(name.toString());
                    }
                }
            });
        } catch (NoSuchFileException e) {
            throw new ModulecException("No module-info.java was found in source directory: " + moduleInfoPath.getParent());
        }

        switch (moduleNames.size()) {
            case 0: throw new ModulecException(moduleInfoPath + " defines no module");
            case 1: return moduleNames.get(0);
            default: throw new ModulecException(moduleInfoPath + " defines multiple modules: " + String.join(", ", moduleNames));
        }
    }
}
