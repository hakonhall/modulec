package no.ion.modulec.compiler;

import no.ion.modulec.file.Pathname;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents the module path (--module-path/-p): A colon-separated list of paths:
 *
 * <ul>
 *     <li>A path to a packaged module: a modular JAR file ending in .jar.</li>
 *     <li>A path to an exploded module: a directory containing a module-info.class file.</li>
 *     <li>A path to directory that may contain any number of packaged or exploded modules.</li>
 * </ul>
 */
public class ModulePath {
    private record Entry(Pathname pathname, String pathString) {}

    private final List<Entry> entries = new ArrayList<>();

    public ModulePath() {}

    public ModulePath clear() {
        entries.clear();
        return this;
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public ModulePath addFrom(ModulePath that) {
        entries.addAll(that.entries);
        return this;
    }

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
        entries.add(new Entry(Pathname.of(path), pathString));
        return this;
    }

    public ModulePath addFromColonSeparatedString(FileSystem fileSystem, String modulePath) {
        Arrays.stream(modulePath.split(":", -1))
              .forEach(path -> entries.add(new Entry(Pathname.of(fileSystem, path), path)));
        return this;
    }

    public List<Path> toPaths() {
        return entries.stream().map(entry -> entry.pathname.path()).collect(Collectors.toList());
    }

    public List<Pathname> toPathnames() {
        return entries.stream().map(Entry::pathname).toList();
    }

    /** Returns the module path to pass as argument to --module-path or -p. */
    public String toColonSeparatedString() {
        return entries.isEmpty() ? "." : entries.stream().map(Entry::pathString).collect(Collectors.joining(":"));
    }

    @Override
    public String toString() { return toColonSeparatedString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModulePath that = (ModulePath) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }
}
