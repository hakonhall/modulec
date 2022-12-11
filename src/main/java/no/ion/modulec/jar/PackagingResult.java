package no.ion.modulec.jar;

import no.ion.modulec.file.Pathname;

import java.util.Objects;

public record PackagingResult(boolean success, String out, Pathname pathname) {
    public PackagingResult {
        Objects.requireNonNull(out, "out cannot be null");
    }
}
