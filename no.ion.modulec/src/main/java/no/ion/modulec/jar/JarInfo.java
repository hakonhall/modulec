package no.ion.modulec.jar;

import no.ion.modulec.file.Pathname;

import java.lang.module.ModuleDescriptor.Version;
import java.util.Optional;

public record JarInfo(Pathname pathname, Optional<String> module, Optional<Version> version) {
}
