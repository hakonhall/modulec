package no.ion.modulec.compiler.single;

import no.ion.modulec.ModuleCompilerException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.compiler.Diagnostic;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.file.SourceDirectory;

import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static no.ion.modulec.util.Exceptions.uncheckIO;

class Javac {
    private final JavaCompiler compiler;

    Javac() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
    }

    static class CompileParams {
        private Pathname classDirectory = null;
        private ModulePath modulePath = new ModulePath();
        private Pathname moduleInfo = null;
        private final List<String> options = new ArrayList<>();
        private final List<CompileParams.Patch> patches = new ArrayList<>();
        private Pathname sourceDirectory = null;
        private boolean verbose;
        private ModuleDescriptor.Version version = null;

        record Patch(String moduleName, Pathname modularJarPathname) {}

        CompileParams() {}

        /** The directory must exist. */
        CompileParams setClassDirectory(Pathname classDirectory) {
            this.classDirectory = Objects.requireNonNull(classDirectory, "classDirectory cannot be null");
            return this;
        }

        CompileParams addModulePathEntriesFrom(ModulePath modulePath) {
            Objects.requireNonNull(modulePath, "modulePath cannot be null");
            this.modulePath.addFrom(modulePath);
            return this;
        }

        CompileParams setModuleInfo(Pathname moduleInfoPathname) {
            this.moduleInfo = moduleInfoPathname;
            return this;
        }

        CompileParams patchModule(String moduleName, Pathname modularJarPathname) {
            this.patches.add(new CompileParams.Patch(moduleName, modularJarPathname));
            return this;
        }

        CompileParams setSourceDirectory(Pathname sourceDirectory) {
            this.sourceDirectory = Objects.requireNonNull(sourceDirectory, "sourceDirectory cannot be null");
            return this;
        }

        CompileParams setVerbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        CompileParams setVersion(ModuleDescriptor.Version version) {
            this.version = Objects.requireNonNull(version, "version cannot be null");
            return this;
        }

        Pathname sourceDirectory() { return sourceDirectory; }
        Pathname moduleInfo() { return moduleInfo; }
        ModulePath mutableModulePath() { return modulePath; }
        Pathname classDirectory() { return classDirectory; }
        ModuleDescriptor.Version version() { return version; }
        List<CompileParams.Patch> patchedModules() { return List.copyOf(patches); }

        // TODO: A. Wire these to modco, B. wire all modco options to here, C. validate these in SingleModuleCompilation,
        // including patchModules moduleName isName() from release().sourceVersion.
        Charset charset() { return Charset.defaultCharset(); }
        Locale locale() { return Locale.getDefault(); }
        List<String> options() { return options; }
        Release release() { return Release.ofJre(); }
        boolean verbose() { return verbose; }
    }

    CompilationResult compile(CompileParams compilation) {
        long startNanos = System.nanoTime();

        var collector = new DiagnosticCollector<JavaFileObject>();
        var writer = new StringWriter();
        boolean success;
        RuntimeException exception = null;
        List<String> javacEquivalentArguments = new ArrayList<>();

        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(collector, compilation.locale(), compilation.charset());
        try {
            compilation.classDirectory().makeDirectories();
            uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(compilation.classDirectory().path())));
            javacEquivalentArguments.add("-d");
            javacEquivalentArguments.add(compilation.classDirectory().path().toString());

            uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.MODULE_PATH, compilation.mutableModulePath().toPaths()));
            javacEquivalentArguments.add("-p");
            javacEquivalentArguments.add(compilation.mutableModulePath().toColonSeparatedString());

            var options = new ArrayList<String>(compilation.options());

            // TODO: --patch-module module=path1:path2:... must be passed via options, as this is not yet supported:
            //uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.PATCH_MODULE_PATH, module, List.of()));
            compilation.patchedModules()
                       .forEach(patch -> {
                           options.add("--patch-module");
                           options.add(patch.moduleName() + "=" + patch.modularJarPathname());
                       });

            if (!compilation.release().matchesJreVersion()) {
                options.add("--release");
                options.add(Integer.toString(compilation.release().releaseInt()));
            }

            ModuleDescriptor.Version version = compilation.version();
            if (version == null)
                throw new ModuleCompilerException("Missing module version");
            options.add("--module-version");
            options.add(version.toString());

            // Avoid generating class files for implicitly referenced files
            options.add("-implicit:none");

            // TODO: Enable dependency generation. Append file=foo?
            // options.add("--debug=completionDeps=source,class");

            javacEquivalentArguments.addAll(options);

            final List<Path> javaPaths;
            Pathname moduleInfo = compilation.moduleInfo();
            if (moduleInfo == null) {
                javaPaths = SourceDirectory.resolveSourceDirectory(compilation.sourceDirectory());
            } else if (!moduleInfo.isFile()) {
                throw new UserErrorException("No such module declaration: " + moduleInfo);
            } else {
                javaPaths = new ArrayList<>(SourceDirectory.resolveSourceDirectory(compilation.sourceDirectory()));
                javaPaths.add(moduleInfo.path());
            }

            if (javaPaths.isEmpty())
                return CompilationResult.ofError(startNanos, "error: no source files found in " + compilation.sourceDirectory());
            Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromPaths(javaPaths);
            compilationUnits.forEach(unit -> javacEquivalentArguments.add(unit.getName()));

            if (compilation.verbose()) {
                System.out.println(javacEquivalentArguments.stream()
                                                           .map(Javac::escapeArgument)
                                                           .collect(Collectors.joining(" ", "javac ", "")));
            }
            JavaCompiler.CompilationTask task = compiler.getTask(writer, standardFileManager, collector, options, null, compilationUnits);
            try {
                success = task.call();
            } catch (IllegalStateException e) {
                success = false;
                exception = e;
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
        return new CompilationResult(success, duration, diagnostics, out,
                                     compilation.classDirectory().path(), exception);
    }

    private static final Pattern MODULE_PATTERN = Pattern.compile("^ *(open +)?module +([a-zA-Z0-9_.]+)", Pattern.MULTILINE);

    private static String resolveModuleName(Pathname sourcePath, SourceVersion release) {
        Pathname moduleInfoPathname = sourcePath.resolve("module-info.java");
        Optional<String> moduleInfo = moduleInfoPathname.readUtf8IfExists();
        if (moduleInfo.isEmpty())
            throw new ModuleCompilerException("Missing module declaration: " + moduleInfoPathname);
        return moduleNameOf(moduleInfoPathname.string(), moduleInfo.get(), release);
    }

    /** TODO: Actually parse the module-info.java with our compiler. */
    private static String moduleNameOf(String moduleInfoPathname, String moduleInfoContent, SourceVersion release) {
        Matcher matcher = MODULE_PATTERN.matcher(moduleInfoContent);
        if (!matcher.find())
            throw new ModuleCompilerException("Failed to find the module name in " + moduleInfoPathname);
        String module = matcher.group(2);
        if (module.isEmpty() || !SourceVersion.isName(module, release))
            throw new ModuleCompilerException("Invalid module name '" + module + "' in " + moduleInfoPathname);
        return module;
    }

    private static OptionalLong positionOf(long position) {
        return position == javax.tools.Diagnostic.NOPOS ? OptionalLong.empty() : OptionalLong.of(position);
    }

    private static String escapeArgument(String argument) {
        if (!needQuoting(argument)) return argument;

        StringBuilder escaped = new StringBuilder().append('"');

        argument.codePoints()
                .forEach(cp -> {
                    switch (cp) {
                        case '$':
                        case '`':
                        case '"':
                        case '\\':
                        case '\n':
                            escaped.append("\\").append(Character.toString(cp));
                            break;
                        default:
                            escaped.append(Character.toString(cp));
                    }
                });

        escaped.append('"');
        return escaped.toString();
    }

    private static boolean needQuoting(String argument) {
        return argument.codePoints()
                .anyMatch(cp -> {
                    if ('a' <= cp && cp <= 'z') return false;
                    if ('A' <= cp && cp <= 'Z') return false;
                    if ('0' <= cp && cp <= '9') return false;
                    if ("_-@%/=+^.:".indexOf(cp) != -1) return false;
                    return true;
                });
    }
}
