package no.ion.modulec.modco;

import no.ion.modulec.UsageException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.file.Pathname;
import org.junit.jupiter.api.Test;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void testEmptyParams() {
        try {
            Options.parse(fileSystem);
            fail();
        } catch (UserErrorException e) {
            assertEquals("Missing required option '--version'", e.getMessage());
        }
    }

    @Test
    void testFullOptions() {
        Options options = Options.parse(fileSystem,
                                        "-g", "",
                                        "-e", "a.main.Klass",
                                        "-p", "a:b",
                                        "-o", "target",
                                        "-P", "foobin=no.ion.example.Main",
                                        "-l", "9",
                                        "-r", "src/main/resources",
                                        "-s", "src/main/java",
                                        "-I", "src/main/java/module-info.java",
                                        "-R", "src/test/resources",
                                        "-t", "src/test/java",
                                        "-b",
                                        "-v", "1.2.3",
                                        "-w", "-serial");
        ModuleCompiler.MakeParams params = options.params();
        assertEquals(Optional.empty(), params.debug());
        assertEquals(Optional.of("a.main.Klass"), params.mainClass());
        assertEquals("a:b", params.modulePath().toColonSeparatedString());
        assertEquals(Pathname.of(fileSystem, "target"), params.out());
        assertEquals(List.of(new ProgramSpec("foobin", "no.ion.example.Main")), params.programs());
        assertEquals(Release.fromFeatureReleaseCounter(9), params.release());;
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/main/resources"))), params.resourceDirectories());
        assertEquals(Pathname.of(fileSystem.getPath("src/main/java")), params.sourceDirectory());
        assertEquals(Optional.of(Pathname.of(fileSystem, "src/main/java/module-info.java")), params.testModuleInfo());
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/test/resources"))), params.testResourceDirectories());
        assertEquals(Optional.of(Pathname.of(fileSystem.getPath("src/test/java"))), params.testSourceDirectory());
        assertTrue(params.verbose());
        assertEquals(ModuleDescriptor.Version.parse("1.2.3"), params.version());
        assertEquals(Optional.of("-serial"), params.warnings());
    }

    @Test
    void testSpecial() {
        Options options = Options.parse(fileSystem,
                                        "-v", "1.2.3",
                                        "-w", "");  // disable -Xlint
        ModuleCompiler.MakeParams params = options.params();
        assertEquals(Optional.empty(), params.warnings());  // disables -Xlint
    }

    @Test
    void testMinimal() {
        Options options = Options.parse(fileSystem, "-v", "1.2.3");
        ModuleCompiler.MakeParams params = options.params();
        assertEquals(Optional.of(""), params.debug());
        assertEquals(Optional.empty(), params.mainClass());
        assertEquals(".", params.modulePath().toColonSeparatedString());
        assertEquals(Pathname.of(fileSystem, "out"), params.out());
        assertEquals(List.of(), params.programs());
        assertEquals(Release.ofJre(), params.release());;
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/main/resources"))), params.resourceDirectories());
        assertEquals(Pathname.of(fileSystem.getPath("src/main/java")), params.sourceDirectory());
        assertEquals(Optional.empty(), params.testModuleInfo());
        assertEquals(List.of(Pathname.of(fileSystem.getPath("src/test/resources"))), params.testResourceDirectories());
        assertEquals(Optional.of(Pathname.of(fileSystem.getPath("src/test/java"))), params.testSourceDirectory());
        assertFalse(params.verbose());
        assertEquals(ModuleDescriptor.Version.parse("1.2.3"), params.version());
        assertEquals(Optional.of("all"), params.warnings());
    }
}