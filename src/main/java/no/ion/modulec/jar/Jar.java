package no.ion.modulec.jar;

import no.ion.modulec.file.Pathname;
import no.ion.modulec.ModuleCompilerException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

public class Jar {
    private final ToolProvider jarTool;
    public Jar() {
        jarTool = ToolProvider.findFirst("jar").orElseThrow(() -> new ModuleCompilerException("No jar tool found"));
    }

    public PackagingResult pack(ModulePackaging packaging) {
        var arguments = new ArrayList<String>();
        arguments.add(packaging.action());
        arguments.add("-f"); // --file
        arguments.add(packaging.jarFile().toString());

        ModuleDescriptor.Version version = packaging.version();
        if (version != null) {
            arguments.add("--module-version");
            arguments.add(version.toString());
        }

        // With --no-manifest, jar(1) ignores --manifest and --main-class.
        if (packaging.manifest() != null) {
            if (packaging.manifest().isPresent()) {
                arguments.add("-m"); // --manifest
                arguments.add(packaging.manifest().get().toString());
            } else {
                if (packaging.mainClass() != null)
                    throw new ModuleCompilerException("--main-class is set, but it will be ignored by jar(1) with --no-module");
                arguments.add("-M"); // --no-manifest
            }
        }
        if (packaging.mainClass() != null) {
            arguments.add("-e");  // --main-class
            arguments.add(packaging.mainClass());
        }

        for (var include : packaging.includes()) {
            arguments.add("-C");
            arguments.add(include.directory().toString());
            for (var path : include.pathsRelativeDirectory())
                arguments.add(path.toString());
        }

        var writer = new StringWriter();
        var printWriter = new PrintWriter(writer);
        String[] args = arguments.toArray(String[]::new);

        if (packaging.verbose())
            System.out.println(arguments.stream().collect(Collectors.joining(" ", "jar ", "")));

        // JarToolProvider returns 0 on success and 1 on failure.
        boolean success = jarTool.run(printWriter, printWriter, args) == 0;
        printWriter.flush(); // also flushes writer
        String out = writer.toString();
        printWriter.close(); // also closes writer
        return new PackagingResult(success, out, Pathname.of(packaging.jarFile()));
    }
}
