package no.ion.modulec.jar;

import no.ion.modulec.ModuleCompilerException;
import no.ion.modulec.file.Pathname;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Class to create a fat JAR by copying and extending an existing JAR.
 */
public class FatJar {
    /** The path of the module directory in a fat JAR, WITH an ending '/'. */
    public static String MODULE_DIRECTORY = "META-INF/mod/";

    public void extend(FatJarSpec extension) {
        if (!extension.baseJar().isFile())
            throw new ModuleCompilerException("No such JAR file: " + extension.baseJar());
        extension.outputJar().makeParentDirectories();

        try {
            try (OutputStream outputStream = Files.newOutputStream(extension.outputJar().path(),
                                                                   StandardOpenOption.CREATE,
                                                                   StandardOpenOption.TRUNCATE_EXISTING,
                                                                   StandardOpenOption.WRITE)) {
                if (extension.header() != null)
                    outputStream.write(extension.header());

                try (JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
                    try (JarFile baseJarFile = new JarFile(extension.baseJar().file())) {
                        copyAndExtendJar(baseJarFile, extension.outputJar().path(), jarOutputStream, extension.adds());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private void copyAndExtendJar(JarFile baseJar, Path outputPath, JarOutputStream outputJar,
                                  List<FatJarSpec.AddSpec> adds) throws IOException {
        Map<String, Pathname> additions = new HashMap<>();
        adds.forEach(spec -> {
            if (additions.containsKey(spec.pathInJar())) {
                Pathname previousPathname = additions.get(spec.pathInJar());
                if (!Objects.equals(Optional.ofNullable(previousPathname).map(Pathname::normalize),
                                    Optional.ofNullable(spec.filePathname()).map(Pathname::normalize))) {
                    throw new ModuleCompilerException("Duplicate entry for " + spec.pathInJar() + ": " +
                                                      spec.filePathname() + " vs " + previousPathname);
                }
            }
            additions.put(spec.pathInJar(), spec.filePathname());

            // Validate now that we anyway are looping over the add-specs
            if (spec.filePathname() != null && !spec.filePathname().isFile())
                throw new ModuleCompilerException("Unable to add " + spec.filePathname() + " to " +
                                                  outputPath + ": Not a file");
        });

        for (Enumeration<JarEntry> jarEntries = baseJar.entries(); jarEntries.hasMoreElements();) {
            JarEntry entry = jarEntries.nextElement();
            String entryPath = entry.getName();
            if (additions.containsKey(entryPath)) {
                Pathname overridingPathname = additions.remove(entryPath);
                // Instead of the entry in baseJar, copy the replacement to outputJar.
                if (entryPath.endsWith("/")) {
                    // I.e. directory
                    JarEntry newEntry = new JarEntry(entryPath);
                    outputJar.putNextEntry(newEntry);
                    outputJar.closeEntry();
                } else {
                    // I.e. regular file
                    File overridingFile = overridingPathname.file();
                    JarEntry newEntry = new JarEntry(entryPath);
                    newEntry.setTime(overridingFile.lastModified());
                    outputJar.putNextEntry(newEntry);
                    try (InputStream inputStream = Files.newInputStream(overridingPathname.path())) {
                        inputStream.transferTo(outputJar);
                    }
                    outputJar.closeEntry();
                }
            } else {
                // Copy the entry from baseJar to outputJar.
                JarEntry newEntry = new JarEntry(entry);
                outputJar.putNextEntry(newEntry);
                try (InputStream inputStream = baseJar.getInputStream(entry)) {
                    inputStream.transferTo(outputJar);
                }
                outputJar.closeEntry();
            }
        }

        // Add all files not already added
        additions.forEach((pathInJar, pathnameOnDisk) -> {
            JarEntry newEntry = new JarEntry(pathInJar);

            try {
                if (pathInJar.endsWith("/")) {
                    outputJar.putNextEntry(newEntry);
                } else {
                    newEntry.setTime(pathnameOnDisk.file().lastModified());
                    outputJar.putNextEntry(newEntry);
                    try (InputStream inputStream = Files.newInputStream(pathnameOnDisk.path())) {
                        inputStream.transferTo(outputJar);
                    }
                }

                outputJar.closeEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
