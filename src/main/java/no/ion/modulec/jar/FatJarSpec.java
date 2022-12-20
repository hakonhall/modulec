package no.ion.modulec.jar;

import no.ion.modulec.file.Pathname;

import java.util.ArrayList;
import java.util.List;

public class FatJarSpec {
    private final Pathname baseJar;
    private final Pathname outputJar;
    private byte[] header = null;
    private final List<AddSpec> adds = new ArrayList<>();

    public static record AddSpec(Pathname filePathname, String pathInJar) {}

    public FatJarSpec(Pathname baseJar, Pathname outputJar) {
        this.baseJar = baseJar;
        this.outputJar = outputJar;
    }

    public FatJarSpec addHeader(byte[] header) {
        this.header = header;
        return this;
    }

    public FatJarSpec addFile(Pathname file, String pathInJar) {
        adds.add(new AddSpec(file, pathInJar));
        return this;
    }

    public Pathname baseJar() { return baseJar; }
    public Pathname outputJar() { return outputJar; }
    public byte[] header() { return header; }
    public List<AddSpec> adds() { return List.copyOf(adds); }
}
