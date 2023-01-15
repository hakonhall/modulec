package no.ion.modulec;

import no.ion.modulec.compiler.Release;
import no.ion.modulec.compiler.multi.ModuleCompilationAndPackaging;
import no.ion.modulec.compiler.multi.MultiModuleCompilationAndPackaging;
import no.ion.modulec.compiler.multi.MultiModuleCompilationAndPackagingResult;
import no.ion.modulec.compiler.multi.MultiModuleCompiler;
import no.ion.modulec.util.command.ArgumentException;
import no.ion.modulec.util.command.Options;
import no.ion.modulec.util.command.ProgramArgumentParser;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

public class ModuleCompiler2 {
    public static void main(String... args) {
        System.exit(mainWithExitCode(args));
    }

    public static int mainWithExitCode(String... args) {
        FileSystem fileSystem = FileSystems.getDefault();

        final Options options;
        try {
            options = ProgramArgumentParser.parse(fileSystem, args);
        } catch (ArgumentException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        var compilation = new MultiModuleCompilationAndPackaging(Release.ofJre());

        compilation.setBuildDirectory(options.topLevelOptions().buildDirectory());
        compilation.addOptions(options.topLevelOptions().options());
        compilation.modulePath().clear().addFrom(options.topLevelOptions().modulePath());

        for (var moduleOptions : options.moduleOptions()) {
            ModuleCompilationAndPackaging module = compilation.addModule();
            moduleOptions.destination().ifPresent(module::setClassOutputDirectory);
            module.setJarPath(moduleOptions.jarOutput().orElse(null));
            moduleOptions.mainClass().ifPresent(module::setMainClass);
            moduleOptions.manifest().ifPresent(module::addManifest);
            moduleOptions.moduleName().ifPresent(module::setName);
            moduleOptions.version().ifPresent(module::setVersion);
            module.addSourceDirectories(moduleOptions.sources());
            List<Path> toInclude = List.of(fileSystem.getPath("."));
            moduleOptions.resources().forEach(resourceDirectory -> module.addResources(resourceDirectory, toInclude));
        }

        var compiler = new MultiModuleCompiler();

        MultiModuleCompilationAndPackagingResult result;
        try {
            result = compiler.make(compilation);
        } catch (ModuleCompilerException e) {
            System.out.println("modc: " + e.getMessage());
            return 1;
        }

        if (!result.cResult().success()) {
            System.out.println(result.cResult().message());
            return 1;
        } else {
            boolean success = true;
            for (var entry : result.pResults().entrySet()) {
                if (!entry.getValue().success()) {
                    success = false;
                    System.out.println(entry.getValue().out());
                }
            }
            if (!success)
                return 1;
        }

        return 0;
    }
}
