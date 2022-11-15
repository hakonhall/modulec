package no.ion.modulec;

import no.ion.modulec.java.ModuleCompilationAndPackaging;
import no.ion.modulec.java.ModuleCompiler;
import no.ion.modulec.java.MultiModuleCompilationAndPackaging;
import no.ion.modulec.java.MultiModuleCompilationAndPackagingResult;
import no.ion.modulec.java.Release;
import no.ion.modulec.util.ModuleCompilerException;
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

        compilation.setOutputDirectory(options.topLevelOptions().work());
        compilation.addOptions(options.topLevelOptions().options());
        compilation.modulePath().clear().addFrom(options.topLevelOptions().modulePath());

        for (var moduleOptions : options.moduleOptions()) {
            ModuleCompilationAndPackaging module = compilation.addModule();
            moduleOptions.destination().ifPresent(module::setClassOutputDirectory);
            module.setJarPath(moduleOptions.jarFile().orElse(null));
            moduleOptions.mainClass().ifPresent(module::setMainClass);
            moduleOptions.manifest().ifPresent(module::addManifest);
            moduleOptions.moduleName().ifPresent(module::setName);
            moduleOptions.version().ifPresent(module::setVersion);
            module.addSourceDirectories(moduleOptions.sources());
            List<Path> toInclude = List.of(fileSystem.getPath("."));
            moduleOptions.resources().forEach(resourceDirectory -> module.addResources(resourceDirectory, toInclude));
        }

        var compiler = new ModuleCompiler();

        MultiModuleCompilationAndPackagingResult result;
        try {
            result = compiler.make(compilation);
        } catch (ModuleCompilerException e) {
            System.out.println(e.getMessage());
            return 1;
        }

        if (!result.cResult().success()) {
            System.out.println(result.cResult().makeMessage());
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
