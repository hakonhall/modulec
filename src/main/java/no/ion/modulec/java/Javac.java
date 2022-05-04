package no.ion.modulec.java;

import no.ion.modulec.file.Pathname;
import no.ion.modulec.util.ModuleCompilerException;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class Javac {
    private final JavaCompiler compiler;

    public Javac() { this.compiler = getSystemJavaCompiler(); }

    public static class Params {
        private final ModulePath modulePath = new ModulePath();
        private final List<String> options = new ArrayList<>();
        private final List<ModuleInfo> moduleInfos = new ArrayList<>();
        // Should actually be one of com.sun.tools.javac.platform.PlatformProvider.getSupportedPlatformNames().
        private int release = Runtime.version().feature();
        private Locale locale = Locale.getDefault();
        private Charset charset = StandardCharsets.UTF_8;

        public Params() {}

        public ModulePath modulePath() { return modulePath; }

        public Params withModulePath(Consumer<ModulePath> callback) {
            callback.accept(modulePath);
            return this;
        }

        public Params addOptions(String... options) {
            Collections.addAll(this.options, options);
            return this;
        }

        private record ModuleInfo(List<Path> sourceDirectories, Path destinationDirectory) {}

        /**
         * Compile the module with the give source directories and write the class files to the destination directory.
         * There must be exactly one module-info.java in one of the source directories that defines the module.
         */
        public Params addModule(List<Path> sourceDirectories, Path destinationDirectory) {
            // This corresponds to the module-specific form of --module-source-path, see e.g.
            // <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html#the-module-source-path-option">The
            // Module Source Path Option</a>.

            Objects.requireNonNull(destinationDirectory, "destinationDirectory cannot be null");
            if (sourceDirectories.isEmpty())
                throw new IllegalArgumentException("There must be at least one source directory");
            Objects.requireNonNull(sourceDirectories, "sourceDirectories cannot be null");
            moduleInfos.add(new ModuleInfo(sourceDirectories, destinationDirectory));
            return this;
        }

        /** Set the release to compile for, e.g. 17. By default, the current feature version of this JDK. */
        public Params setRelease(int release) {
            try {
                SourceVersion.valueOf("RELEASE_" + release);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown release: " + release);
            }
            this.release = release;
            return this;
        }

        public Params setLocale(Locale locale) {
            this.locale = Objects.requireNonNull(locale, "Locale cannot be null");
            return this;
        }

        public Params setCharset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "Charset cannot be null");
            return this;
        }

        private SourceVersion sourceVersion() {
            return SourceVersion.valueOf("RELEASE_" + release);
        }
    }

    public record Diagnostic(Kind kind,
                             Optional<JavaFileObject> source,
                             OptionalLong position,
                             OptionalLong startPosition,
                             OptionalLong endPosition,
                             OptionalLong lineNumber,
                             OptionalLong columnNumber,
                             Optional<String> code,
                             String message) {
        public Diagnostic {
            Objects.requireNonNull(kind, "kind cannot be null");
            Objects.requireNonNull(source, "source cannot be null");
            Objects.requireNonNull(position, "position cannot be null");
            Objects.requireNonNull(startPosition, "startPosition cannot be null");
            Objects.requireNonNull(endPosition, "endPosition cannot be null");
            Objects.requireNonNull(lineNumber, "lineNumber cannot be null");
            Objects.requireNonNull(columnNumber, "columnNumber cannot be null");
            Objects.requireNonNull(code, "code cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
        }
    }

    public record Result(boolean success, Duration duration, List<Diagnostic> diagnostics, String out,
                         RuntimeException exception) {
        public Result {
            Objects.requireNonNull(duration, "duration cannot be null");
            Objects.requireNonNull(duration, "diagnostics cannot be null");
            Objects.requireNonNull(duration, "out cannot be null");
        }

        /** Tries to make a message similar to that produced by the javac tool. */
        public String makeMessage() {
            if (exception != null) {
                return exception.getMessage();
            }

            int errors = 0;

            var buffer = new StringBuilder();
            for (var diagnostic : diagnostics) {
                if (diagnostic.kind == Kind.ERROR) {
                    errors += 1;
                }

                if (diagnostic.source.isPresent()) {
                    buffer.append(diagnostic.source().get().getName());
                    diagnostic.lineNumber().ifPresent(lineNumber -> buffer.append(':').append(lineNumber));
                    buffer.append(": ");
                    if (diagnostic.kind == Kind.ERROR) {
                        buffer.append("error: ");
                    } else if (diagnostic.kind == Kind.WARNING || diagnostic.kind == Kind.MANDATORY_WARNING) {
                        buffer.append("warning: ");
                    }
                    buffer.append(diagnostic.message).append('\n');

                    diagnostic.lineNumber.ifPresent(lineno -> {
                        CharSequence charSequence = uncheckIO(() -> diagnostic.source().get().getCharContent(true));
                        if (charSequence != null) {
                            String content = String.valueOf(charSequence);
                            String[] lines = content.lines().toArray(String[]::new);
                            if (lineno - 1 >= 0 && lineno - 1 < lines.length) {
                                String line = lines[(int) (lineno - 1)];
                                buffer.append(line).append('\n');
                                diagnostic.columnNumber.ifPresent(columnNumber -> {
                                    if (columnNumber > 1)
                                        buffer.append(" ".repeat((int) (columnNumber - 1)));
                                    buffer.append("^\n");
                                });
                            }
                        }
                    });
                }
            }

            if (errors > 0) {
                buffer.append(errors).append(errors == 1 ? " error\n" : " errors\n");
            }

            if (!out.isEmpty())
                buffer.append(out);

            buffer.append(success ? "OK\n" : "FAILED\n");
            buffer.append(String.format("Completed in %.3fs\n", duration.toNanos() / 1000_000.0));
            return buffer.toString();
        }
    }

    public Result compile(Params params) {
        long startNanos = System.nanoTime();

        var collector = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(collector, params.locale, params.charset);
        var writer = new StringWriter();
        boolean success;
        RuntimeException exception = null;
        Pathname temporaryDirectory = Pathname.defaultTemporaryDirectory().makeTemporaryDirectory(Javac.class.getName() + ".", "").directory();
        try {
            // 1. For some reason, Java requires setLocationFromPaths(CLASS_OUTPUT, ...) (-d) when
            //    setLocationForModule(MODULE_SOURCE_PATH, ...) (--module-source-path) is set.  We output non-module
            //    specific class files to a temporary directory, and verify none files were written.
            // 2. setLocationFromPaths() must be called before setLocationForModule(), as the former clears the latter.
            uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(temporaryDirectory.path())));
            uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.MODULE_PATH, params.modulePath.toPaths()));

            Set<String> modules = new HashSet<>(params.moduleInfos.size());
            for (var moduleInfo : params.moduleInfos) {
                String module = findModuleName(moduleInfo.sourceDirectories, params.sourceVersion());
                if (!modules.add(module))
                    throw new ModuleCompilerException("Module added twice: " + module);

                uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.MODULE_SOURCE_PATH, module, moduleInfo.sourceDirectories()));
                // setLocationForModule fails unless destination directory exists.
                Pathname.of(moduleInfo.destinationDirectory()).makeDirectories();
                uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.CLASS_OUTPUT, module, List.of(moduleInfo.destinationDirectory())));

                // This doesn't fail, but it doesn't work either: Module B depends on A.  Module B gets a module path
                // pointing to the exploded module of A.  Compilation of B fails with "module not found: example.A".
                // This is likely OK: Use the union of module paths for the compile.
                //uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.MODULE_PATH, module, moduleInfo.modulePath().toPaths()));

                // TODO: --patch-module module=path1:path2:... must be passed via options, as this is not yet supported:
                //uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.PATCH_MODULE_PATH, module, List.of()));
            }

            List<Path> allJavaSourcePaths = findAllJavaSourcePaths(params.moduleInfos);
            Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromPaths(allJavaSourcePaths);

            final List<String> options;
            if (params.release == Runtime.version().feature()) {
                options = params.options;
            } else {
                options = new ArrayList<>(params.options);
                options.add("--release");
                options.add(Integer.toString(params.release));
            }

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
            int numDeleted = temporaryDirectory.deleteRecursively();
            if (numDeleted > 1) {
                throw new ModuleCompilerException((numDeleted - 1) + " files were written to the temporary and " +
                                                  "non-module output directory for class files!?");
            }
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
                        diagnostic.getMessage(params.locale)))
                .collect(Collectors.toList());

        String out = writer.toString();
        var duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new Result(success, duration, diagnostics, out, exception);
    }

    private static final Pattern MODULE_PATTERN = Pattern.compile("^ *(open +)?module +([a-zA-Z0-9_.]+)");

    private String findModuleName(List<Path> sources, SourceVersion release) {
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

    private List<Path> findAllJavaSourcePaths(List<Params.ModuleInfo> moduleInfos) {
        return moduleInfos.stream()
                          .flatMap(moduleInfo -> moduleInfo.sourceDirectories.stream())
                          .map(Pathname::of)
                          .flatMap(pathname -> pathname.find(true,
                                                             (subpathname, attribute) ->
                                                                     attribute.isFile() && subpathname.filename().endsWith(".java") ?
                                                                     Optional.of(subpathname.path()) :
                                                                     Optional.empty())
                                                       .stream())
                          .collect(Collectors.toList());
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
