package no.ion.modulec.compiler.multi;

import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.jar.Jar;
import no.ion.modulec.jar.ModulePackaging;
import no.ion.modulec.jar.PackagingResult;
import no.ion.modulec.modco.ProgramContext;

import java.util.Map;
import java.util.TreeMap;

public class MultiModuleCompiler {
    private final Compiler compiler = new Compiler();
    private final Jar jar = new Jar(new ProgramContext());

    public MultiModuleCompilationAndPackagingResult make(MultiModuleCompilationAndPackaging job) {
        CompilationResult cResult = compiler.compile(job);
        if (!cResult.success())
            return new MultiModuleCompilationAndPackagingResult(cResult, Map.of());

        var pResults = new TreeMap<String, PackagingResult>();

        // TODO: Parallelize this
        for (ModuleCompilationAndPackaging module : job.modules()) {
            module.resolveJarFile();
            var modulePackaging = ModulePackaging.forCreatingJar(module.jarPath().orElseThrow());
            module.version().ifPresent(modulePackaging::setVersion);
            modulePackaging.addFiles(module.classOutputDirectory().orElseThrow(), null);
            for (ModuleCompilationAndPackaging.Resource resource : module.resources()) {
                modulePackaging.addFiles(resource.rootDirectory(), resource.toInclude());
            }
            if (module.manifest() != null)
                modulePackaging.setManifest(module.manifest().orElse(null));
            module.mainClass().ifPresent(modulePackaging::setMainClass);
            PackagingResult pResult = jar.pack(modulePackaging);
            pResults.put(module.name().orElseThrow(), pResult);
        }

        return new MultiModuleCompilationAndPackagingResult(cResult, pResults);
    }
}
