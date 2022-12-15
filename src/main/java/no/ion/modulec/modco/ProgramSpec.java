package no.ion.modulec.modco;

import java.util.Objects;

public class ProgramSpec {
    private final String filename;
    private final String mainClass;

    public ProgramSpec(String filename, String mainClass) {
        this.filename = Objects.requireNonNull(filename, "filename cannot be null");
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass cannot be null");
    }

    public String filename() { return filename; }
    public String mainClass() { return mainClass; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProgramSpec that = (ProgramSpec) o;
        return filename.equals(that.filename) && mainClass.equals(that.mainClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, mainClass);
    }
}
