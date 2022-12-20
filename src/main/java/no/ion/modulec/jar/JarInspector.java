package no.ion.modulec.jar;

import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.file.UncheckedInputStream;
import no.ion.modulec.module.ModuleVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class JarInspector {
    public static JarInfo infoOf(Path jarPath) {
        Pathname jarPathname = Pathname.of(jarPath);
        Optional<ModuleDescriptor> descriptor = moduleDescriptorOf(jarPathname);
        return new JarInfo(jarPathname,
                           descriptor.map(ModuleDescriptor::name),
                           descriptor.flatMap(ModuleDescriptor::version));
    }

    public static Map<ModuleVersion, HybridModularJarInfo> hybridModulesOf(ModulePath modulePath) {
        Map<ModuleVersion, HybridModularJarInfo> map = new HashMap<>();

        for (Pathname pathname : modulePath.toPathnames()) {
            if (pathname.isFile() && pathname.filename().endsWith(".jar")) {
                hybridModularJarInfoOf(pathname).ifPresent(info -> map.put(info.id(), info));
            } else if (pathname.isDirectory()) {
                if (pathname.resolve("module-info.class").isFile()) {
                    try (UncheckedInputStream inputStream = pathname.newInputStream()) {
                        hybridModularModuleInfo(inputStream)
                                .ifPresent(id -> map.put(id, new HybridModularJarInfo(id, pathname)));
                    }
                } else {
                    pathname.forEachDirectoryEntry(directoryEntry -> {
                        if (directoryEntry.isFile() && directoryEntry.filename().endsWith(".jar"))
                            hybridModularJarInfoOf(directoryEntry).ifPresent(info -> map.put(info.id(), info));
                        // Support 'pathname' being a directory of exploded JAR directories? ...
                    });
                }
            }
        }

        return map;
    }

    public static Optional<HybridModularJarInfo> hybridModularJarInfoOf(Pathname jarPathname) {
        Optional<ModuleDescriptor> descriptor = moduleDescriptorOf(jarPathname);
        if (descriptor.isEmpty() || descriptor.get().version().isEmpty()) return Optional.empty();
        return Optional.of(new HybridModularJarInfo(new ModuleVersion(descriptor.get().name(),
                                                                      descriptor.get().version().get()),
                                                    jarPathname));
    }

    public static Optional<ModuleVersion> hybridModularModuleInfo(InputStream inputStream) {
        ModuleDescriptor descriptor = uncheckIO(() -> ModuleDescriptor.read(inputStream));
        return descriptor.version().map(version -> new ModuleVersion(descriptor.name(), version));
    }

    public static Optional<ModuleDescriptor> moduleDescriptorOf(Pathname jarPathname) {
        if (!jarPathname.isFile() || !jarPathname.filename().endsWith(".jar"))
            return Optional.empty();

        try (JarFile jarFile = new JarFile(jarPathname.file())) {

            JarEntry moduleInfoClassEntry = jarFile.getJarEntry("module-info.class");
            if (moduleInfoClassEntry == null)
                return Optional.empty();

            try (InputStream moduleInfoClassInputStream = jarFile.getInputStream(moduleInfoClassEntry)) {
                ModuleDescriptor moduleDescriptor = ModuleDescriptor.read(moduleInfoClassInputStream);
                return Optional.of(moduleDescriptor);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
