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

    static class ModulecException extends RuntimeException {
        public ModulecException(String message) { super(message); }
    }

    public static void help() {
        throw new ModulecException("Usage: modulec [OPTION...] SRC [-- JAVACARG...]\n" +
                "Create a modular JAR file from module source in SRC.\n" +
                "\n" +
                "Options:\n" +
                "  -C RSRC...              Add the content of the RSRC directory to the jar file.\n" +
                "  -d DEST                 Output compiled class files to DEST.\n" +
                "  -e,--main-class CLASS   Specify the qualified main class.  If CLASS starts\n" +
                "                          with '.' the main class will be MODULE.CLASS.\n" +
                "  -f,--file JARPATH       Specifies the filename of the modular JAR, by default\n" +
                "                          MODULE[-VERSION].jar.  If JARPATH is a directory, the\n" +
                "                          JAR is written to that directory with the default\n" +
                "                          filename.  Ending JARPATH with / forces creation of\n" +
                "                          that directory.\n" +
                "  -p,--module-path MPATH  The colon-separated module path.\n" +
                "  -v,--version VERSION    The module version.\n" +
                "\n" +
                "MODULE is read from SRC/module-info.java.");
    }

    public static void main(String... args) {
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

    public modulec(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    /** As much of main() as possible that can be unit tested. */
    int noExitMain(String... args) {
        var additionalJarDirectoryPaths = new ArrayList<String>();
        Path destPath = null;
        String jarPath = null;
        String mainClass = null;
        String modulePath = null;
        String version = null;

        int i;
        for (i = 0; i < args.length; ++i) {
            if (args[i].equals("-C")) {
                if (++i >= args.length) {
                    throw new ModulecException("error: " + args[i] + " requires an argument");
                }
                if (!Files.isDirectory(fileSystem.getPath(args[i]))) {
                    throw new ModulecException("error: " + args[i] + " is not a directory");
                }
                additionalJarDirectoryPaths.add(args[i]);
            } else if (args[i].equals("-d")) {
                if (++i >= args.length) {
                    throw new ModulecException("error: " + args[i] + " requires an argument");
                }
                destPath = fileSystem.getPath(args[i]);
            } else if (args[i].equals("-e") || args[i].equals("--main-class")) {
                if (++i >= args.length) {
                    throw new ModulecException("error: " + args[i] + " requires an argument");
                }
                mainClass = args[i];
            } else if (args[i].equals("-f") || args[i].equals("--file")) {
                if (++i >= args.length) {
                    throw new ModulecException("error: " + args[i] + " requires an argument");
                }
                jarPath = args[i];
            } else if (args[i].equals("-h") || args[i].equals("--help")) {
                help();
            } else if (args[i].equals("-p") || args[i].equals("--module-path")) {
                if (++i >= args.length) {
                    throw new ModulecException("error: " + args[i] + "requires an argument");
                }
                modulePath = args[i];
            } else if (args[i].equals("-v") || args[i].equals("--version")) {
                if (++i >= args.length) {
                    throw new ModulecException("error: " + args[i] + " requires an argument");
                }
                version = args[i];
            } else {
                break;
            }
        }

        if (destPath == null) {
            throw new ModulecException("error: -d is required");
        }

        if (i >= args.length) {
            throw new ModulecException("error: missing module source directory argument");
        }
        Path sourcePath = fileSystem.getPath(args[i++]);
        if (!Files.isDirectory(sourcePath)) {
            throw new ModulecException("error: module source directory does not exist: " + sourcePath);
        }

        if (i < args.length) {
            if (args[i].equals("--")) {
                ++i;
            } else {
                throw new ModulecException("error: found stray argument: " + args[i]);
            }
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String moduleName = getModuleName(compiler, sourcePath.resolve("module-info.java"));

        var javacDestPath = destPath.resolve(".classes");
        var moduleSourcePath = destPath.resolve(".src");

        ensureSymlinkTo(javacDestPath.resolve(moduleName), "..");
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
        Arrays.stream(args, i, args.length).forEach(javacArgs::add);

        int compileExitCode = compiler.run(System.in, System.out, System.out, javacArgs.toArray(String[]::new));

        delete(moduleSourcePath.resolve(moduleName));
        delete(moduleSourcePath);
        delete(javacDestPath.resolve(moduleName));
        delete(javacDestPath);

        if (compileExitCode != 0) {
            return compileExitCode;
        }

        var jarArgs = new ArrayList<String>();
        jarArgs.add("-c");

        String defaultJarFilename = moduleName + (version == null ? "" : "-" + version) + ".jar";
        if (jarPath == null) {
            jarPath = defaultJarFilename;
        } else if (jarPath.endsWith("/")) {
            jarPath += defaultJarFilename;
        }
        jarArgs.add("-f");
        jarArgs.add(jarPath);

        if (mainClass != null) {
            if (mainClass.startsWith(".")) {
                mainClass = moduleName + mainClass;
            }
            jarArgs.add("-e");
            jarArgs.add(mainClass);
        }

        jarArgs.add("-C");
        jarArgs.add(destPath.toString());
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

    private void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensureSymlinkTo(Path path, String target) {
        try {
            try {
                Path destSymlink = Files.readSymbolicLink(path);
                if (destSymlink.toString().equals(target)) {
                    return;  // already correct
                }

                Files.delete(path);
            } catch (NoSuchFileException ignored) {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            Files.createSymbolicLink(path, fileSystem.getPath(target));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String getModuleName(JavaCompiler compiler, Path moduleInfoPath) {
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        switch (moduleNames.size()) {
            case 0: throw new ModulecException(moduleInfoPath + " defines no module");
            case 1: return moduleNames.get(0);
            default: throw new ModulecException(moduleInfoPath + " defines multiple modules: " + String.join(", ", moduleNames));
        }
    }
}
