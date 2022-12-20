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
            Pathname previousPathname = additions.put(spec.pathInJar(), spec.filePathname());
            if (previousPathname != null) {
                if (!Objects.equals(previousPathname.path().normalize(),
                                    spec.filePathname().path().normalize())) {
                    throw new ModuleCompilerException("Duplicate entry for " + spec.pathInJar() + ": " +
                                                      spec.filePathname() + " vs " + previousPathname);
                }
            }

            // Validate now that we anyway are looping over the add-specs
            if (!spec.filePathname().isFile())
                throw new ModuleCompilerException("Unable to add " + spec.filePathname() + " to " +
                                                  outputPath + ": Not a file");
        });

        for (Enumeration<JarEntry> jarEntries = baseJar.entries(); jarEntries.hasMoreElements();) {
            JarEntry entry = jarEntries.nextElement();
            String entryPath = entry.getName();
            Pathname overridingPathname = additions.remove(entryPath);
            if (overridingPathname == null) {
                // Copy the entry from baseJar to outputJar.
                JarEntry newEntry = new JarEntry(entry);
                outputJar.putNextEntry(newEntry);
                try (InputStream inputStream = baseJar.getInputStream(entry)) {
                    inputStream.transferTo(outputJar);
                }
                outputJar.closeEntry();
            } else {
                // Instead of the entry in baseJar, copy the replacement to outputJar.
                File overridingFile = overridingPathname.file();
                JarEntry newEntry = new JarEntry(entryPath);
                newEntry.setTime(overridingFile.lastModified());
                outputJar.putNextEntry(newEntry);
                try (InputStream inputStream = Files.newInputStream(overridingPathname.path())) {
                    inputStream.transferTo(outputJar);
                }
                outputJar.closeEntry();
            }
        }

        // Add all files not already added
        additions.forEach((pathInJar, pathnameOnDisk) -> {
            try {
                JarEntry newEntry = new JarEntry(pathInJar);
                newEntry.setTime(pathnameOnDisk.file().lastModified());
                outputJar.putNextEntry(newEntry);
                try (InputStream inputStream = Files.newInputStream(pathnameOnDisk.path())) {
                    inputStream.transferTo(outputJar);
                }
                outputJar.closeEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
