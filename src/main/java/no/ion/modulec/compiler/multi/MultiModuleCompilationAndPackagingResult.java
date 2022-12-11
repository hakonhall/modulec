package no.ion.modulec.compiler.multi;

import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.jar.PackagingResult;

import java.util.Map;

public record MultiModuleCompilationAndPackagingResult(CompilationResult cResult, Map<String, PackagingResult> pResults) {
}
