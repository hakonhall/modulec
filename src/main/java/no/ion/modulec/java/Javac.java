package no.ion.modulec.java;

import no.ion.modulec.file.BasicAttributes;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.file.TemporaryDirectory;
import no.ion.modulec.util.ModuleCompilerException;

import javax.lang.model.SourceVersion;
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

public class Javac {
    private static final String OWNER_FILENAME = "owner";
    private static final String OWNER_MAGIC = "no.ion.modulec";

    private final JavaCompiler compiler;

    public Javac() { this.compiler = getSystemJavaCompiler(); }

    public CompilationResult compile(MultiModuleCompilationAndPackaging compilation) {
        long startNanos = System.nanoTime();

        var collector = new DiagnosticCollector<JavaFileObject>();
        var writer = new StringWriter();
        boolean success;
        RuntimeException exception = null;

        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(collector, compilation.locale(), compilation.charset());
        try {
            try (OutputDirectory outputDirectory = resolveOutputDirectory(compilation.outputDirectory().orElse(null))) {
                compilation.setOutputDirectory(outputDirectory.directory().path());
                // For some reason Java requires setLocationFromPaths(CLASS_OUTPUT, ...) (aka -d) is invoked when
                // setLocationForModule(MODULE_SOURCE_PATH, ...) (aka --module-source-path) is set.  We output non-module-specific
                // class files to a special classes directory, and verify no files were written by the compiler (see finally).
                Pathname classOutput = resolveClassDirectory(outputDirectory);
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

                        String moduleName = resolveModuleName(module.name().orElse(null), module.sourceDirectories(), compilation.release().sourceVersion());
                        if (!moduleNames.add(moduleName))
                            return CompilationResult.ofError(startNanos, "error: module added twice: " + moduleName + "\n");
                        module.setName(moduleName);

                        uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.MODULE_SOURCE_PATH, moduleName, module.sourceDirectories()));

                        // setLocationForModule fails unless destination directory exists.
                        Pathname moduleClassesDirectory = resolveModuleClassesDirectory(module.classOutputDirectory(),
                                                                                        outputDirectory, moduleName);
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

                    var options = new ArrayList<String>();

                    options.addAll(compilation.options());

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
        return new CompilationResult(success, duration, diagnostics, out, exception);
    }

    private List<Path> sourceFiles(List<Path> sourceDirectories) {
        return sourceDirectories.stream()
                .map(Pathname::of)
                .flatMap(pathname -> {
                    if (!pathname.isDirectory())
                        throw new ModuleCompilerException("No such source directory: " + pathname);

                    return pathname.find(true,
                                         (subpathname, attribute) ->
                                                 attribute.isFile() && subpathname.filename().endsWith(".java") ?
                                                         Optional.of(subpathname.path()) :
                                                         Optional.empty())
                                   .stream();
                })
                .collect(Collectors.toList());
    }

    private Pathname resolveModuleClassesDirectory(Optional<Path> moduleClassOutputDirectory,
                                                   OutputDirectory outputDirectory,
                                                   String moduleName) {
        return moduleClassOutputDirectory.map(Pathname::of)
                                         .orElseGet(() -> outputDirectory.directory().resolve(moduleName).resolve("classes"));

    }

    private Pathname resolveClassDirectory(OutputDirectory outputDirectory) {
        return outputDirectory.directory().resolve("classes");
    }

    private record OutputDirectory(Pathname directory, boolean isTemporary) implements TemporaryDirectory {
        @Override
        public void close() {
            if (isTemporary)
                directory.deleteRecursively();
        }
    }

    private OutputDirectory resolveOutputDirectory(Path outputDirectoryPath) {
        if (outputDirectoryPath == null) {
            // Use a temporary output directory
            Pathname outputDirectory = Pathname.makeTmpdir(Javac.class.getName() + ".", "", null).directory();
            outputDirectory.resolve(OWNER_FILENAME).writeUtf8(OWNER_MAGIC);
            return new OutputDirectory(outputDirectory, true);
        } else {
            Pathname outputDirectory = Pathname.of(outputDirectoryPath);
            Pathname ownerFile = outputDirectory.resolve(OWNER_FILENAME);
            Optional<BasicAttributes> outputDirectoryAttributes = outputDirectory.readAttributesIfExists(true);
            if (outputDirectoryAttributes.isEmpty()) {
                // New output directory
                outputDirectory.makeDirectory();
                ownerFile.writeUtf8(OWNER_MAGIC);
                return new OutputDirectory(outputDirectory, false);
            } else if (outputDirectoryAttributes.get().isDirectory()) {
                Optional<String> ownerContent = ownerFile.readUtf8IfExists();
                if (ownerContent.isPresent()) {
                    if (!ownerContent.get().equals(OWNER_MAGIC))
                        throw new ModuleCompilerException("Output directory must be created and managed by no.ion.modulec: " + outputDirectory);
                    // Reusing output directory created earlier
                    return new OutputDirectory(outputDirectory, false);
                } else {
                    if (!outputDirectory.isEmptyDirectory())
                        throw new ModuleCompilerException("Refuse to use non-empty output directory: " + outputDirectory);
                    // Claim empty output directory
                    ownerFile.writeUtf8(OWNER_MAGIC);
                    return new OutputDirectory(outputDirectory, false);
                }
            } else {
                throw new UncheckedIOException(new NotDirectoryException(outputDirectory.string()));
            }
        }
    }

    private static final Pattern MODULE_PATTERN = Pattern.compile("^ *(open +)?module +([a-zA-Z0-9_.]+)");

    private String resolveModuleName(String moduleName, List<Path> sources, SourceVersion release) {
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
            // TODO: Actually parse the files with our compiler.
            Matcher matcher = MODULE_PATTERN.matcher(moduleInfo.get());
            if (!matcher.find())
                throw new ModuleCompilerException("Failed to find the module name in " + moduleInfoJavaPathname.string());
            module = matcher.group(2);
            if (!SourceVersion.isName(module, release))
                throw new ModuleCompilerException("Invalid module name '" + module + "' in " + moduleInfoJavaPathname.string());
        }

        if (module == null)
            throw new ModuleCompilerException("No module-info.java found in any of the source directories: " + sources);

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
