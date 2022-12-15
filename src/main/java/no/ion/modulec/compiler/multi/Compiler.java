package no.ion.modulec.compiler.multi;

import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.compiler.Diagnostic;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.file.BasicAttributes;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.file.SourceDirectory;
import no.ion.modulec.file.TemporaryDirectory;
import no.ion.modulec.util.ModuleCompilerException;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class Compiler {
    private static final String OWNER_FILENAME = "owner";
    private static final String OWNER_MAGIC = "no.ion.modulec";

    private final JavaCompiler compiler;

    public Compiler() { this.compiler = getSystemJavaCompiler(); }

    public CompilationResult compile(MultiModuleCompilationAndPackaging compilation) {
        long startNanos = System.nanoTime();

        var collector = new DiagnosticCollector<JavaFileObject>();
        var writer = new StringWriter();
        boolean success;
        RuntimeException exception = null;

        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(collector, compilation.locale(), compilation.charset());
        try {
            try (BuildDirectory buildDirectory = resolveBuildDirectory(compilation.buildDirectory().orElse(null))) {
                compilation.setBuildDirectory(buildDirectory.directory().path());
                // For some reason Java requires setLocationFromPaths(CLASS_OUTPUT, ...) (aka -d) is invoked when
                // setLocationForModule(MODULE_SOURCE_PATH, ...) (aka --module-source-path) is set.  We output non-module-specific
                // class files to a special classes directory, and verify no files were written by the compiler (see finally).
                Pathname classOutput = resolveClassDirectory(buildDirectory);
                try {
                    // These setLocationFromPaths() must be called before setLocationForModule(), as the former clears the latter.
                    // And it must exist.
                    classOutput.makeDirectories();
                    uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput.path())));
                    uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.MODULE_PATH, compilation.modulePath().toPaths()));

                    int nModules = compilation.modules().size();
                    if (nModules == 0)
                        return CompilationResult.ofError(startNanos, "error: no modules\n");
                    var moduleNames = new HashSet<String>(nModules);
                    var sourcePaths = new ArrayList<Path>();
                    for (var module : compilation.modules()) {
                        List<Path> sourceDirectories = module.sourceDirectories();
                        if (sourceDirectories.isEmpty())
                            return CompilationResult.ofError(startNanos, "error: no source directories" +
                                    module.name().map(n -> " for module " + n).orElse("") + "\n");

                        List<Path> moduleSourcePaths = sourceFiles(sourceDirectories);
                        if (moduleSourcePaths.isEmpty())
                            return CompilationResult.ofError(startNanos, "error: no source files found in " +
                                    (sourceDirectories.size() == 1 ?
                                            sourceDirectories.get(0) :
                                            sourceDirectories) + "\n");

                        sourcePaths.addAll(moduleSourcePaths);

                        String moduleName = resolveModuleName(module.name().orElse(null), module.sourceDirectories(), compilation.release());
                        if (!moduleNames.add(moduleName))
                            return CompilationResult.ofError(startNanos, "error: module added twice: " + moduleName + "\n");
                        module.setName(moduleName);

                        uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.MODULE_SOURCE_PATH, moduleName, module.sourceDirectories()));

                        // setLocationForModule fails unless destination directory exists.
                        Pathname moduleClassesDirectory = resolveModuleClassesDirectory(module.classOutputDirectory(),
                                                                                        buildDirectory, moduleName);
                        module.setClassOutputDirectory(moduleClassesDirectory.path());
                        moduleClassesDirectory.makeDirectories();
                        uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.CLASS_OUTPUT, moduleName, List.of(moduleClassesDirectory.path())));

                        // This doesn't fail, but it doesn't work either: Module B depends on A.  Module B gets a module path
                        // pointing to the exploded module of A.  Compilation of B fails with "module not found: example.A".
                        // This is likely OK: Use the union of module paths for the compile.
                        uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.MODULE_PATH, moduleName, module.modulePath().toPaths()));

                        // TODO: --patch-module module=path1:path2:... must be passed via options, as this is not yet supported:
                        //uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.PATCH_MODULE_PATH, module, List.of()));
                    }

                    Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromPaths(sourcePaths);

                    var options = new ArrayList<String>(compilation.options());

                    if (!compilation.release().matchesJreVersion()) {
                        options.add("--release");
                        options.add(Integer.toString(compilation.release().releaseInt()));
                    }

                    // Avoid generating class files for implicitly referenced files
                    options.add("-implicit:none");

                    // TODO: Enable dependency generation. Append file=foo?
                    // options.add("--debug=completionDeps=source,class");

                    JavaCompiler.CompilationTask task = compiler.getTask(writer, standardFileManager, collector, options, null, compilationUnits);

                    try {
                        success = task.call();
                    } catch (IllegalStateException e) {
                        success = false;
                        exception = e;
                    }
                } finally {
                    // Since we force all class files to be written to module-specific destination directories, no class
                    // files should be written to the non-module-specific destination directory.  As noted above, we
                    // need to specify such a directory, as the javac tool required it (for unknown reason - a bug?).
                    // If the compiler actually writes files to the destination directory, we need to figure out what
                    // that means, so throw an exception to fail as loudly as we can.
                    int numDeleted = classOutput.deleteRecursively();
                    if (numDeleted > 1) {
                        throw new ModuleCompilerException((numDeleted - 1) + " files were written to the temporary and " +
                                                                  "non-module output directory for class files!?");
                    }
                }
            }
        } finally {
            uncheckIO(standardFileManager::close);
        }

        List<Diagnostic> diagnostics = collector
                .getDiagnostics()
                .stream()
                .map(diagnostic -> new Diagnostic(
                        diagnostic.getKind(),
                        Optional.ofNullable(diagnostic.getSource()),
                        positionOf(diagnostic.getPosition()),
                        positionOf(diagnostic.getStartPosition()),
                        positionOf(diagnostic.getEndPosition()),
                        positionOf(diagnostic.getLineNumber()),
                        positionOf(diagnostic.getColumnNumber()),
                        Optional.ofNullable(diagnostic.getCode()),
                        diagnostic.getMessage(compilation.locale())))
                .collect(Collectors.toList());

        String out = writer.toString();
        var duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new CompilationResult(success, duration, diagnostics, out, null, exception);
    }

    private static List<Path> sourceFiles(List<Path> sourceDirectories) {
        return sourceDirectories.stream()
                                .map(Pathname::of)
                                .map(SourceDirectory::resolveSourceDirectory)
                                .flatMap(List::stream)
                                .collect(Collectors.toList());
    }

    private Pathname resolveModuleClassesDirectory(Optional<Path> moduleClassOutputDirectory,
                                                   BuildDirectory buildDirectory,
                                                   String moduleName) {
        return moduleClassOutputDirectory.map(Pathname::of)
                                         .orElseGet(() -> buildDirectory.directory().resolve(moduleName).resolve("classes"));

    }

    private Pathname resolveClassDirectory(BuildDirectory buildDirectory) {
        return buildDirectory.directory().resolve("classes");
    }

    private record BuildDirectory(Pathname directory, boolean isTemporary) implements TemporaryDirectory {
        @Override
        public void close() {
            if (isTemporary)
                directory.deleteRecursively();
        }
    }

    private BuildDirectory resolveBuildDirectory(Path buildDirectoryPath) {
        if (buildDirectoryPath == null) {
            // Use a temporary build directory
            Pathname buildDirectory = Pathname.makeTmpdir(Compiler.class.getName() + ".", "", null).directory();
            buildDirectory.resolve(OWNER_FILENAME).writeUtf8(OWNER_MAGIC);
            return new BuildDirectory(buildDirectory, true);
        } else {
            Pathname buildDirectory = Pathname.of(buildDirectoryPath);
            Pathname ownerFile = buildDirectory.resolve(OWNER_FILENAME);
            Optional<BasicAttributes> buildDirectoryAttributes = buildDirectory.readAttributesIfExists(true);
            if (buildDirectoryAttributes.isEmpty()) {
                // New build directory
                buildDirectory.makeDirectories();
                ownerFile.writeUtf8(OWNER_MAGIC);
                return new BuildDirectory(buildDirectory, false);
            } else if (buildDirectoryAttributes.get().isDirectory()) {
                Optional<String> ownerContent = ownerFile.readUtf8IfExists();
                if (ownerContent.isPresent()) {
                    if (!ownerContent.get().equals(OWNER_MAGIC))
                        throw new ModuleCompilerException("Build directory must be created and managed by no.ion.modulec: " + buildDirectory);
                    // Reusing build directory created earlier
                    return new BuildDirectory(buildDirectory, false);
                } else {
                    if (!buildDirectory.isEmptyDirectory())
                        throw new ModuleCompilerException("Refuse to use non-empty build directory: " + buildDirectory);
                    // Claim empty build directory
                    ownerFile.writeUtf8(OWNER_MAGIC);
                    return new BuildDirectory(buildDirectory, false);
                }
            } else {
                throw new UncheckedIOException(new NotDirectoryException(buildDirectory.string()));
            }
        }
    }

    private static final Pattern MODULE_PATTERN = Pattern.compile("^ *(open +)?module +([a-zA-Z0-9_.]+)", Pattern.MULTILINE);

    private String resolveModuleName(String moduleName, List<Path> sources, Release release) {
        if (moduleName != null)
            return moduleName;
        String module = null;
        for (Path sourcePath : sources) {
            Pathname moduleInfoJavaPathname = Pathname.of(sourcePath).resolve("module-info.java");
            Optional<String> moduleInfo = moduleInfoJavaPathname.readUtf8IfExists();
            if (moduleInfo.isEmpty())
                continue;
            if (module != null)
                throw new ModuleCompilerException("Found more than one module-info.java in the module source directories: " + sources);
            module = moduleNameOf(moduleInfoJavaPathname.string(), moduleInfo.get(), release);
        }

        if (module == null)
            throw new ModuleCompilerException("No module-info.java found in any of the source directories: " + sources);

        return module;
    }

    /** TODO: Actually parse the module-info.java with our compiler. */
    static String moduleNameOf(String moduleInfoJavaPathname, String moduleInfoContent, Release release) {
        Matcher matcher = MODULE_PATTERN.matcher(moduleInfoContent);
        if (!matcher.find())
            throw new ModuleCompilerException("Failed to find the module name in " + moduleInfoJavaPathname);
        String module = matcher.group(2);
        if (!release.isName(module))
            throw new ModuleCompilerException("Invalid module name '" + module + "' in " + moduleInfoJavaPathname);
        return module;
    }

    private static JavaCompiler getSystemJavaCompiler() {
        JavaCompiler systemJavaCompiler = ToolProvider.getSystemJavaCompiler();
        if (systemJavaCompiler == null)
            throw new ModuleCompilerException("No system Java compiler was found");
        return systemJavaCompiler;
    }

    private static OptionalLong positionOf(long position) {
        return position == javax.tools.Diagnostic.NOPOS ? OptionalLong.empty() : OptionalLong.of(position);
    }
}
