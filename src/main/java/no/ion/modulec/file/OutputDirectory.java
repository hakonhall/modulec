package no.ion.modulec.file;

import no.ion.modulec.UserErrorException;

import java.util.Optional;

public class OutputDirectory {
    private final Pathname out;
    private final Owner owner;

    private Pathname outputClassDirectory = null;
    private Pathname outputTestClassDirectory = null;
    private String jarFilename = null;
    private Pathname jar = null;
    private Pathname testJar = null;
    private Pathname programJar = null;
    private Pathname programDirectory = null;

    public enum Owner {
        COMPILER_SINGLE("compiler.single");

        private final String magic;

        Owner(String id) {
            this.magic = "no.ion.modulec." + id;
        }
    }

    /** Create the output directory if it does not already exist, throwing UserErrorException if invalid. */
    public static OutputDirectory create(Pathname directory, Owner owner) {
        return new OutputDirectory(directory, owner).create();
    }

    private OutputDirectory(Pathname out, Owner owner) {
        this.out = out;
        this.owner = owner;
    }

    private Pathname ownerPathname() { return out.resolve("owner"); }

    /** Creates the output directory for the class files from the compilation of the source files, if not already done. */
    public Pathname outputClassDirectory() {
        if (outputClassDirectory == null) {
            outputClassDirectory = out.resolve("classes");
            outputClassDirectory.makeDirectory();
        }
        return outputClassDirectory;
    }

    /** Creates the output directory for the class files from the compilation of the test source files, if not already done. */
    public Pathname outputTestClassDirectory() {
        if (outputTestClassDirectory == null) {
            outputTestClassDirectory = out.resolve("test/classes");
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
            jar = out.resolve(jarFilename);
        }
        return jar;
    }

    /** Prerequisite: {@link #setJarFilename(String)} must already have been invoked. */
    public Pathname testJarPathname() {
        if (testJar == null) {
            if (jarFilename == null)
                throw new IllegalStateException("jar filename not set");
            Pathname testDirectory = out.resolve("test");
            testDirectory.makeDirectory();
            testJar = testDirectory.resolve(jarFilename);
        }
        return testJar;
    }

    public Pathname programJarPath() {
        if (programJar == null) {
            programJar = out.resolve("fat.jar");
        }
        return programJar;
    }

    /** Creates the program directory if it does not exist yet. Returns the program directory. */
    public Pathname programDirectory() {
        if (programDirectory == null) {
            programDirectory = out.resolve("bin");
            programDirectory.makeDirectory();
        }
        return programDirectory;
    }

    private OutputDirectory create() {
        if (!out.exists()) {
            out.makeDirectories();
            ownerPathname().writeUtf8(owner.magic);
            return this;
        }

        if (!out.isDirectory())
            throw new UserErrorException("Output is not a directory: " + out);

        if (out.isEmptyDirectory()) {
            ownerPathname().writeUtf8(owner.magic);
            return this;
        }

        Optional<String> magic = ownerPathname().readUtf8IfExists();
        if (magic.isPresent() && magic.get().equals(owner.magic))
            return this;

        throw new UserErrorException("Refuse to use non-empty output directory: " + out);
    }
}
