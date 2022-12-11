package no.ion.modulec.file;

import no.ion.modulec.ModuleCompilerException;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Can be used for both source and test source directories.
 */
public class SourceDirectory {
    public static List<Path> resolveSourceDirectory(Pathname sourceDirectory) {
        if (!sourceDirectory.isDirectory())
            throw new ModuleCompilerException("Source directory does not exist: " + sourceDirectory);

        return sourceDirectory.find(true,
                                    (subpathname, attribute) ->
                                            attribute.isFile() && subpathname.filename().endsWith(".java") ?
                                            Optional.of(subpathname.path()) :
                                            Optional.empty());
    }
}
