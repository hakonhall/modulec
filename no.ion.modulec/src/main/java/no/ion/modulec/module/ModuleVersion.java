package no.ion.modulec.module;

import java.lang.module.ModuleDescriptor.Version;
import java.util.Objects;
import java.util.Optional;

public record ModuleVersion(String name, Optional<Version> version) {
    public ModuleVersion {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
    }

    @Override
    public String toString() {
        return name + '@' + version;
    }
}
