package no.ion.modulec.modco;

import no.ion.modulec.UsageException;
import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.file.Pathname;
import org.junit.jupiter.api.Test;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class OptionsTest {

    private final FileSystem fileSystem = FileSystems.getDefault();

    @Test
    void testUsage() {
        try {
            Options.parse(fileSystem, "-h");
            fail();
        } catch (UsageException e) {
            assertTrue(e.getMessage().startsWith("Usage: modco "));
            Pathname path = Pathname.of(fileSystem.getPath("src/main/resources/no/ion/modulec/modco.usage"));
            String usage = path.readUtf8();
            assertEquals(usage, e.getMessage());
        }
    }

    @Test
    void testFullOptions() {
        Options options = Options.parse(fileSystem,
                                        "-r", "src/main/resources",
                                        "-s", "src/main/java",
                                        "-u", "src/test/resources",
                                        "-t", "src/test/java",
                                        "-v", "1.2.3");
        ModuleCompiler.MakeParams compilation = options.params();
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/main/resources"))), compilation.resourceDirectories());
        assertEquals(Pathname.of(fileSystem.getPath("src/main/java")), compilation.sourceDirectory());
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/test/resources"))), compilation.testResourceDirectories());
        assertEquals(Pathname.of(fileSystem.getPath("src/test/java")), compilation.testSourceDirectory());
        assertEquals(ModuleDescriptor.Version.parse("1.2.3"), compilation.version());
    }

    @Test
    void testDefaults() {
        Options options = Options.parse(fileSystem,
                                        "-v", "1.2.3");
        ModuleCompiler.MakeParams compilation = options.params();
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/main/resources"))), compilation.resourceDirectories());
        assertEquals(Pathname.of(fileSystem.getPath("src/main/java")), compilation.sourceDirectory());
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/test/resources"))), compilation.testResourceDirectories());
        assertEquals(Pathname.of(fileSystem.getPath("src/test/java")), compilation.testSourceDirectory());
        assertEquals(ModuleDescriptor.Version.parse("1.2.3"), compilation.version());
    }
}