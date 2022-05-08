package no.ion.modulec.java;

import java.util.Map;

public record MultiModuleCompilationAndPackagingResult(CompilationResult cResult, Map<String, PackagingResult> pResults) {
}
