package no.ion.modulec.jar;

import no.ion.modulec.file.Pathname;
import no.ion.modulec.module.ModuleVersion;

public record HybridModularJarInfo(ModuleVersion id, Pathname location) {
}
