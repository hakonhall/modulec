package no.ion.modulec.file;

import no.ion.modulec.ModuleCompilerException;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Can be used for both source and test source directories.
 */
public class SourceDirectory {
    public static List<Path> resolveSource(Pathname source) {
        Optional<BasicAttributes> attributes = source.readAttributesIfExists(true);
        if (attributes.isEmpty())
            throw new ModuleCompilerException("Source directory does not exist: " + source);

        if (attributes.get().isDirectory()) {
            return source.find(true,
                               (subpathname, attribute) ->
                                       attribute.isFile() && subpathname.filename().endsWith(".java") ?
                                       Optional.of(subpathname.path()) :
                                       Optional.empty());
        } else if (source.toString().endsWith(".java")) {
            return List.of(source.path());
        } else {
            throw new ModuleCompilerException("Invalid source: " + source);
        }
    }
}
