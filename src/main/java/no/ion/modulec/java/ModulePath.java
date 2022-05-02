package no.ion.modulec.java;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents the --module-path/-p: A colon-separated list of paths:
 *
 * <ul>
 *     <li>A path to a packaged module: a modular JAR file ending in .jar.</li>
 *     <li>A path to an exploded module: a directory containing a module-info.class file.</li>
 *     <li>A path to directory that may contain any number of packaged or exploded modules.</li>
 * </ul>
 */
public class ModulePath {
    private record Entry(Path path, String pathString) {}

    private final List<Entry> entries = new ArrayList<>();

    public ModulePath() {}

    /** A packaged module aka a modular JAR. */
    public ModulePath addModularJar(Path path) {
        return addEntry(path);
    }

    /** An exploded module directory. */
    public ModulePath addExplodedModule(Path directory) {
        return addEntry(directory);
    }

    /** A directory containing packaged or exploded modules. */
    public ModulePath addModuleDirectory(Path directory) {
        return addEntry(directory);
    }

    /** A path that has an unknown type (see above). */
    public ModulePath addEntry(Path path) {
        String pathString = path.toString();
        if (pathString.contains(":"))
            throw new IllegalArgumentException("Module path entry cannot contain ':': " + path);
        if (pathString.isEmpty())
            throw new IllegalArgumentException("Empty path");
        entries.add(new Entry(path, pathString));
        return this;
    }

    public List<Path> toPaths() {
        return entries.stream().map(Entry::path).collect(Collectors.toList());
    }

    /** Returns the module path to pass as argument to --module-path or -p. */
    public String toColonSeparatedString() {
        return entries.isEmpty() ? "." : entries.stream().map(Entry::pathString).collect(Collectors.joining(":"));
    }
}
