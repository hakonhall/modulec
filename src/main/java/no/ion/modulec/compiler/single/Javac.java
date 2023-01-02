package no.ion.modulec.compiler.single;

import no.ion.modulec.Context;
import no.ion.modulec.ModuleCompilerException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.compiler.Diagnostic;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.file.BasicAttributes;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.ion.modulec.util.Exceptions.uncheckIO;

class Javac {
    private final Context context;
    private final JavaCompiler compiler;

    Javac(Context context) {
        this.context = context;
        this.compiler = ToolProvider.getSystemJavaCompiler();
    }

    static class CompileParams {
        private Optional<String> debug = Optional.of(""); // => -g
        private Pathname checksumFile = null;
        private Pathname classDirectory = null;
        private ModulePath modulePath = new ModulePath();
        private Pathname moduleInfo = null;
        private final List<String> options = new ArrayList<>();
        private final List<CompileParams.Patch> patches = new ArrayList<>();
        private Release release = Release.ofJre();
        private Pathname sourceDirectory = null;
        private ModuleDescriptor.Version version = null;
        private Optional<String> warnings = Optional.of("all");

        record Patch(String moduleName, Pathname modularJarPathname) {}

        CompileParams() {
            // Fail on warnings
            options.add("-Werror");
            // Avoid generating class files for implicitly referenced files
            options.add("-implicit:none");
        }

        /** The directory must exist. */
        CompileParams setClassDirectory(Pathname classDirectory) {
            this.classDirectory = Objects.requireNonNull(classDirectory, "classDirectory cannot be null");
            return this;
        }

        CompileParams setCompilationChecksumFile(Pathname checksumFile) {
            this.checksumFile = checksumFile;
            return this;
        }

        CompileParams setDebug(Optional<String> debug) {
            Objects.requireNonNull(debug, "debug cannot be null");
            this.debug = debug;
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

        CompileParams setRelease(Release release) {
            this.release = Objects.requireNonNull(release, "release cannot be null");
            return this;
        }

        CompileParams setSourceDirectory(Pathname sourceDirectory) {
            this.sourceDirectory = Objects.requireNonNull(sourceDirectory, "sourceDirectory cannot be null");
            return this;
        }

        CompileParams setVersion(ModuleDescriptor.Version version) {
            this.version = Objects.requireNonNull(version, "version cannot be null");
            return this;
        }

        CompileParams setWarnings(Optional<String> warnings) {
            this.warnings = Objects.requireNonNull(warnings, "warnings cannot be null");
            return this;
        }

        Optional<String> debug() { return debug; }
        Pathname sourceDirectory() { return sourceDirectory; }
        Pathname moduleInfo() { return moduleInfo; }
        ModulePath mutableModulePath() { return modulePath; }
        Pathname classDirectory() { return classDirectory; }
        List<CompileParams.Patch> patchedModules() { return List.copyOf(patches); }
        Release release() { return release; }
        ModuleDescriptor.Version version() { return version; }
        Optional<String> warnings() { return warnings; }

        // TODO: A. Wire these to modco, B. wire all modco options to here, C. validate these in SingleModuleCompilation,
        // including patchModules moduleName isName() from release().sourceVersion.
        Charset charset() { return Charset.defaultCharset(); }
        Locale locale() { return Locale.getDefault(); }
        List<String> options() { return options; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompileParams that = (CompileParams) o;
            return Objects.equals(debug, that.debug) && Objects.equals(classDirectory, that.classDirectory) && Objects.equals(modulePath, that.modulePath) && Objects.equals(moduleInfo, that.moduleInfo) && Objects.equals(options, that.options) && Objects.equals(patches, that.patches) && Objects.equals(release, that.release) && Objects.equals(sourceDirectory, that.sourceDirectory) && Objects.equals(version, that.version) && Objects.equals(warnings, that.warnings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(debug, classDirectory, modulePath, moduleInfo, options, patches, release, sourceDirectory, version, warnings);
        }
    }

    CompilationResult compile(CompileParams compilation) {
        long startNanos = System.nanoTime();

        final List<Pathname> sources;
        Pathname moduleInfo = compilation.moduleInfo();
        if (moduleInfo == null) {
            sources = List.of(compilation.sourceDirectory);
        } else if (moduleInfo.isFile()) {
            sources = List.of(moduleInfo, compilation.sourceDirectory);
        } else {
            throw new UserErrorException("No such module declaration: " + moduleInfo);
        }

        final List<Path> javaPaths;
        if (compilation.checksumFile == null) {
            javaPaths = sources.stream().map(SourceDirectory::resolveSource).flatMap(List::stream).collect(Collectors.toList());
        } else {
            Optional<List<Path>> javaPaths2 = prepareClassDirectory(compilation.classDirectory,
                                                                    sources,
                                                                    compilation.checksumFile,
                                                                    compilation.hashCode());
            if (javaPaths2.isEmpty())
                return new CompilationResult(true,
                                             0,
                                             Duration.ofNanos(System.nanoTime() - startNanos),
                                             List.of(),
                                             "",
                                             compilation.classDirectory.path(),
                                             null);
            javaPaths = javaPaths2.get();
        }

        var collector = new DiagnosticCollector<JavaFileObject>();
        var writer = new StringWriter();
        boolean success;
        RuntimeException exception = null;
        List<String> javacEquivalentArguments = new ArrayList<>();
        javacEquivalentArguments.add("javac");

        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(collector, compilation.locale(), compilation.charset());
        try {
            compilation.classDirectory().makeDirectories();
            uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(compilation.classDirectory().path())));
            javacEquivalentArguments.add("-d");
            javacEquivalentArguments.add(compilation.classDirectory().path().toString());

            ModulePath modulePath = compilation.mutableModulePath();
            if (!modulePath.isEmpty()) {
                uncheckIO(() -> standardFileManager.setLocationFromPaths(StandardLocation.MODULE_PATH, modulePath.toPaths()));
                javacEquivalentArguments.add("-p");
                javacEquivalentArguments.add(modulePath.toColonSeparatedString());
            }

            var options = new ArrayList<String>(compilation.options());

            compilation.warnings().ifPresent(warnings -> {
                if (warnings.equals("all")) {
                    options.add("-Xlint");
                } else {
                    options.add("-Xlint:" + warnings);
                }
            });

            compilation.debug().ifPresent(debug -> {
                if (debug.equals("")) {
                    options.add("-g");
                } else {
                    options.add("-g:" + debug);
                }
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

            // TODO: --patch-module module=path1:path2:... must be passed via options, as this is not yet supported:
            //uncheckIO(() -> standardFileManager.setLocationForModule(StandardLocation.PATCH_MODULE_PATH, module, List.of()));
            compilation.patchedModules()
                       .forEach(patch -> {
                           options.add("--patch-module");
                           options.add(patch.moduleName() + "=" + patch.modularJarPathname());
                       });

            // TODO: Enable dependency generation. Append file=foo?
            // options.add("--debug=completionDeps=source,class");

            javacEquivalentArguments.addAll(options);

            Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromPaths(javaPaths);
            compilationUnits.forEach(unit -> javacEquivalentArguments.add(unit.getName()));

            context.log().command(javacEquivalentArguments);
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

        if (compilation.checksumFile != null && success)
            updateChecksumFile(compilation.checksumFile, compilation.hashCode());

        var duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new CompilationResult(success, javaPaths.size(), duration, diagnostics, out,
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

    /**
     * Remove files and directories from classDirectory that are no longer matched by source files.  Returns
     * Optional.empty() if no source files needs to be recompiled.  Otherwise the *.java source files found in the
     * source directories.
     */
    private Optional<List<Path>> prepareClassDirectory(Pathname classDirectory, List<Pathname> sources,
                                                       Pathname checksumFile, int checksum) {

        // Optimization
        if (!classDirectory.isDirectory() || classDirectory.isEmptyDirectory())
            return Optional.of(sources.stream()
                                      .map(SourceDirectory::resolveSource)
                                      .flatMap(List::stream)
                                      .collect(Collectors.toList()));

        // A source file a/b/Foo.java relative a source directory should result in a whitelist of a/, a/b/, and a/b/Foo.
        // This allows the directories a/ and a/b/ below the class directory, a a/b/Foo.class file, and any files in a/b/
        // with a filename starting with Foo$ and ending in .class (e.g. nested classes of Foo).
        Map<String, BasicAttributes> whitelist = new HashMap<>();

        List<Path> javaFiles = sources
                .stream()
                .map(Pathname::normalize)
                .flatMap(source -> {
                    // Special-case the module-info.java "source", as the only non-directory source
                    if (source.filename().equals("module-info.java")) {
                        whitelist.put("module-info", source.readAttributes(true));
                        return Stream.of(source.path());
                    }

                    return source.find(true, (subpath, attributes) -> {
                        if (!subpath.toString().endsWith(".java"))
                            return Optional.empty();

                        if (source.normalize().toString().equals(subpath.normalize().toString()))
                            if (!attributes.isDirectory())
                                return Optional.of(subpath.path());

                            String prefix = subpath.relative(source).normalize().toString();
                            if (!prefix.endsWith(".java"))
                                return Optional.empty();
                            prefix = prefix.substring(0, prefix.length() - ".java".length());
                            whitelist.put(prefix, attributes);

                            do {
                                int slashIndex = prefix.lastIndexOf('/');
                                if (slashIndex == -1)
                                    break;
                                prefix = prefix.substring(0, slashIndex);
                                if (whitelist.put(prefix + '/', attributes) != null)
                                    break; // already added
                            } while (true);

                            return Optional.of(subpath.path());
                    })
                                 .stream();
                })
                .collect(Collectors.toList());

        final Pathname normalizedClassDirectory = classDirectory.normalize();
        final boolean[] mustCompile = { false };
        normalizedClassDirectory.visit(false, false, (pathname, attributes) -> {
            Pathname lookupKey = pathname.relative(normalizedClassDirectory).normalize();

            if (attributes.isDirectory()) {
                if (!whitelist.containsKey(lookupKey + "/")) {
                    context.log().debugLine(() -> "Deleting directory: " + pathname);
                    pathname.deleteRecursively();
                    mustCompile[0] = true;
                    return Pathname.VisitHint.SKIP;
                }
            } else if (attributes.isFile()) {
                String stem = lookupKey.filename();
                if (stem.endsWith(".class")) {
                    stem = stem.substring(0, stem.length() - ".class".length());
                    int dollarIndex = stem.indexOf('$');
                    if (dollarIndex != -1)
                        stem = stem.substring(0, dollarIndex);
                    lookupKey = lookupKey.parent().resolve(stem).normalize();
                    BasicAttributes sourceAttributes = whitelist.get(lookupKey.toString());
                    if (sourceAttributes == null) {
                        // source file deleted
                        context.log().debugLine(() -> "Deleting orphaned class file: " + pathname);
                        pathname.delete();
                        mustCompile[0] = true;
                    } else if (!sourceAttributes.lastModified().isBefore(attributes.lastModified())) {
                        if (dollarIndex != -1) {
                            context.log().debugLine(() -> "Source about to be recompiled: Deleting derived class: " + pathname);
                            pathname.delete();
                        }
                        mustCompile[0] = true;
                    }
                } else {
                    context.log().debugLine(() -> "Deleting stray file: " + pathname);
                    pathname.delete();
                    mustCompile[0] = true;
                }
            } else {
                context.log().debugLine(() -> "Deleting stray file: " + pathname);
                pathname.delete();
                mustCompile[0] = true;
            }

            return Pathname.VisitHint.CONTINUE;
        });

        return mustCompile[0] || checksumHasChanged(checksumFile, checksum) ? Optional.of(javaFiles) : Optional.empty();
    }

    private boolean checksumHasChanged(Pathname file, int checksum) {
        if (file == null) return true;

        Optional<String> content = file.readUtf8IfExists();
        if (content.isEmpty())
            return true;

        String previousChecksumAsString = content.get().strip();

        final int previousChecksum;
        try {
            previousChecksum = Integer.parseInt(previousChecksumAsString);
        } catch (NumberFormatException e) {
            return true;
        }

        return checksum != previousChecksum;
    }

    private void updateChecksumFile(Pathname file, int checksum) {
        if (checksumHasChanged(file, checksum))
            file.writeUtf8(checksum + "\n");
    }
}
