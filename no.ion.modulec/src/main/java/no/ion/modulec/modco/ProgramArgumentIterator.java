package no.ion.modulec.modco;

import no.ion.modulec.UserErrorException;
import no.ion.modulec.file.BasicAttributes;
import no.ion.modulec.file.Pathname;

import java.nio.file.FileSystem;
import java.util.Optional;

public class ProgramArgumentIterator {
    private final FileSystem fileSystem;
    private final String[] args;
    private int argi = 0;
    private boolean nextSkips2 = false;

    public ProgramArgumentIterator(FileSystem fileSystem, String[] args) {
        this.fileSystem = fileSystem;
        this.args = args;
    }

    public boolean atEnd() { return argi >= args.length; }
    public String arg() { return args[argi]; }

    public void next() {
        if (nextSkips2) {
            argi += 2;
        } else {
            argi++;
        }
        verifyArgi();
        nextSkips2 = false;
    }

    public Pathname getOptionValueAsPathname() {
        return Pathname.of(fileSystem.getPath(getOptionValueString()));
    }

    public Pathname getOptionValueAsExistingSource() {
        Pathname path = getOptionValueAsPathname();
        Optional<BasicAttributes> attributes = path.readAttributesIfExists(true);
        if (attributes.isEmpty())
            throw new UserErrorException("No such file or directory: " + path);
        if (attributes.get().isDirectory())
            return path;
        if (attributes.get().isFile()) {
            if (!path.toString().endsWith(".java"))
                throw new UserErrorException("Not a Java source file: " + path);
            return path;
        }
        throw new UserErrorException("Neither directory nor file: " + path);
    }

    public Pathname getOptionValueAsExistingDirectory() {
        Pathname path = getOptionValueAsPathname();
        if (!path.isDirectory())
            throw new UserErrorException("Directory does not exist: " + path);
        return path;
    }

    public String getOptionValueString() {
        nextSkips2 = true;
        if (argi + 1 >= args.length)
            throw new UserErrorException("Missing value for option '" + arg() + "'");
        return args[argi + 1];
    }

    public int getOptionValueInt() {
        String value = getOptionValueString();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new UserErrorException("Value of option " + arg() + " not an integer");
        }
    }

    private void verifyArgi() {
        if (argi > args.length)
            throw new IllegalStateException("Have iterated past the end of the program argument list");
    }
}
