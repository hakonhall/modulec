package no.ion.modulec.file;

import no.ion.modulec.UserErrorException;

import java.util.Optional;

public class DestinationDirectory {
    private final Pathname destination;
    private final Owner owner;

    private Pathname outputClassDirectory = null;
    private Pathname outputTestClassDirectory = null;
    private String jarFilename = null;
    private Pathname jar = null;
    private Pathname testJar = null;

    public enum Owner {
        COMPILER_SINGLE("compiler.single");

        private final String magic;

        Owner(String id) {
            this.magic = "no.ion.modulec." + id;
        }
    }

    /** Create the destination directory if it does not already exist, throwing UserErrorException if invalid. */
    public static DestinationDirectory create(Pathname directory, Owner owner) {
        return new DestinationDirectory(directory, owner).create();
    }

    private static Pathname ownerPathname(Pathname destinationDirectory) { return destinationDirectory.resolve("owner"); }

    private DestinationDirectory(Pathname destination, Owner owner) {
        this.destination = destination;
        this.owner = owner;
    }

    /** Creates the output directory for the class files from the compilation of the source files, if not already done. */
    public Pathname outputClassDirectory() {
        if (outputClassDirectory == null) {
            outputClassDirectory = destination.resolve("classes");
            outputClassDirectory.makeDirectory();
        }
        return outputClassDirectory;
    }

    /** Creates the output directory for the class files from the compilation of the test source files, if not already done. */
    public Pathname outputTestClassDirectory() {
        if (outputTestClassDirectory == null) {
            outputTestClassDirectory = destination.resolve("test/classes");
            outputTestClassDirectory.makeDirectories();
        }
        return outputTestClassDirectory;
    }

    public void setJarFilename(String filename) {
        if (this.jarFilename != null)
            throw new IllegalStateException("jar filename already set");
        if (filename.contains("/") || !filename.endsWith(".jar"))
            throw new IllegalArgumentException("Illegal JAR filename: " + filename);
        this.jarFilename = filename;
    }

    /** Prerequisite: {@link #setJarFilename(String)} must already have been invoked. */
    public Pathname jarPathname() {
        if (jar == null) {
            if (jarFilename == null)
                throw new IllegalStateException("jar filename not set");
            jar = destination.resolve(jarFilename);
        }
        return jar;
    }

    /** Prerequisite: {@link #setJarFilename(String)} must already have been invoked. */
    public Pathname testJarPathname() {
        if (testJar == null) {
            if (jarFilename == null)
                throw new IllegalStateException("jar filename not set");
            testJar = destination.resolve("test").resolve(jarFilename);
            testJar.makeParentDirectories();
        }
        return testJar;
    }

    private DestinationDirectory create() {
        if (!destination.exists()) {
            destination.makeDirectories();
            ownerPathname(destination).writeUtf8(owner.magic);
            return this;
        }

        if (!destination.isDirectory())
            throw new UserErrorException("Destination is not a directory: " + destination);

        if (destination.isEmptyDirectory()) {
            ownerPathname(destination).writeUtf8(owner.magic);
            return this;
        }

        Optional<String> magic = ownerPathname(destination).readUtf8IfExists();
        if (magic.isPresent() && magic.get().equals(owner.magic))
            return this;

        throw new UserErrorException("Refuse to use non-empty destination directory: " + destination);
    }
}
