package no.ion.modulec.modco;

import java.util.Objects;
import java.util.Optional;

public class ProgramSpec {
    private final String filename;
    private final String mainClass;

    public ProgramSpec(String filename, String mainClass) {
        this.filename = Objects.requireNonNull(filename, "filename cannot be null");
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass cannot be null");
    }

    /** Denotes a program using the main class of the module. */
    public ProgramSpec(String filename) {
        this.filename = filename;
        this.mainClass = null;
    }

    public String filename() { return filename; }
    public Optional<String> mainClass() { return Optional.ofNullable(mainClass); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProgramSpec that = (ProgramSpec) o;
        return filename.equals(that.filename) && Objects.equals(mainClass, that.mainClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, mainClass);
    }
}
