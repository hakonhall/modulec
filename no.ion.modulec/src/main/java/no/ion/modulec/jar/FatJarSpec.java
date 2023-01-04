package no.ion.modulec.jar;

import no.ion.modulec.file.Pathname;

import java.util.ArrayList;
import java.util.List;

public class FatJarSpec {
    private final Pathname baseJar;
    private final Pathname outputJar;
    private byte[] header = null;
    private final List<AddSpec> adds = new ArrayList<>();

    public static record AddSpec(Pathname filePathname, String pathInJar) {
        public AddSpec {
            if (pathInJar == null)
                throw new NullPointerException("pathInJar cannot be null");

            if (pathInJar.endsWith("/")) {
                if (filePathname != null)
                    throw new IllegalArgumentException("A directory JAR entry (" + pathInJar + ") with a disk location: " +
                                                       filePathname);
            } else {
                if (filePathname == null)
                    throw new IllegalArgumentException("A file JAR entry (" + pathInJar + ") without a disk location");
            }
        }
        public boolean isDirectory() { return pathInJar.endsWith("/"); }
    }

    public FatJarSpec(Pathname baseJar, Pathname outputJar) {
        this.baseJar = baseJar;
        this.outputJar = outputJar;
    }

    public FatJarSpec addHeader(byte[] header) {
        this.header = header;
        return this;
    }

    public FatJarSpec addDirectory(String pathInJar) {
        if (!pathInJar.endsWith("/"))
            pathInJar += "/";
        adds.add(new AddSpec(null, pathInJar));
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
