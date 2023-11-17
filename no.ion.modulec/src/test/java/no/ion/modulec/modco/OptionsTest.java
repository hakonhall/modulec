package no.ion.modulec.modco;

import no.ion.modulec.UsageException;
import no.ion.modulec.UserErrorException;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.file.Pathname;
import org.junit.jupiter.api.Test;

import java.lang.module.ModuleDescriptor;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class OptionsTest {

    private final ProgramContext context = new ProgramContext();

    @Test
    void testUsage() {
        try {
            Options.parse(context, "-h");
            fail();
        } catch (UsageException e) {
            assertTrue(e.getMessage().startsWith("Usage: modco "));
            Pathname path = context.pathname("src/main/resources/no/ion/modulec/modco.usage");
            String usage = path.readUtf8();
            assertEquals(usage, e.getMessage());
        }
    }

    @Test
    void testEmptyParams() {
        Options options = Options.parse(context);
        assertEquals(Optional.empty(), options.params().version());
    }

    @Test
    void testFullOptions() {
        Options options = Options.parse(context,
                                        "-g", "",
                                        "-e", "a.main.Klass",
                                        "-p", "a:b",
                                        "-o", "target",
                                        "-P", "foobin=no.ion.example.Main",
                                        "-l", "9",
                                        "-r", "src/main/resources",
                                        "-s", "src/main/java",
                                        "-R", "src/test/resources",
                                        "-t", "src/test/java",
                                        "-t", "src/test/module-info.java",
                                        "-b",
                                        "-v", "1.2.3",
                                        "-w", "-serial");
        ModuleCompiler.MakeParams params = options.params();
        assertEquals(Optional.empty(), params.debug());
        assertEquals(Optional.of("a.main.Klass"), params.mainClass());
        assertEquals("a:b", params.modulePath().toColonSeparatedString());
        assertEquals(context.pathname("target"), params.out());
        assertEquals(List.of(new ProgramSpec("foobin", "no.ion.example.Main")), params.programs());
        assertEquals(Release.fromFeatureReleaseCounter(9), params.release());;
        assertEquals(List.of(context.pathname("src/main/resources")), params.resourceDirectories());
        assertEquals(List.of(context.pathname("src/main/java")), params.sourceDirectories());
        assertEquals(List.of(context.pathname("src/test/resources")), params.testResourceDirectories());
        assertEquals(List.of(context.pathname("src/test/java"), context.pathname("src/test/module-info.java")), params.testSourceDirectories());
        assertTrue(context.showCommands());
        assertTrue(context.showDebug());
        assertEquals(Optional.of(ModuleDescriptor.Version.parse("1.2.3")), params.version());
        assertEquals(Optional.of("-serial"), params.warnings());
    }

    @Test
    void testSpecial() {
        Options options = Options.parse(context,
                                        "-v", "1.2.3",
                                        "-w", "");  // disable -Xlint
        ModuleCompiler.MakeParams params = options.params();
        assertEquals(Optional.empty(), params.warnings());  // disables -Xlint
    }

    @Test
    void testMinimal() {
        Options options = Options.parse(context, "-v", "1.2.3");
        ModuleCompiler.MakeParams params = options.params();
        assertEquals(Optional.of(""), params.debug());
        assertEquals(Optional.empty(), params.mainClass());
        assertEquals(".", params.modulePath().toColonSeparatedString());
        assertEquals(context.pathname("out"), params.out());
        assertEquals(List.of(), params.programs());
        assertEquals(Release.ofJre(), params.release());;
        assertEquals(List.of(context.pathname("src/main/resources")), params.resourceDirectories());
        assertEquals(List.of(context.pathname("src/main/java")), params.sourceDirectories());
        assertEquals(List.of(context.pathname("src/test/resources")), params.testResourceDirectories());
        assertEquals(List.of(context.pathname("src/test/java")), params.testSourceDirectories());
        assertFalse(context.showCommands());
        assertFalse(context.showDebug());
        assertEquals(Optional.of(ModuleDescriptor.Version.parse("1.2.3")), params.version());
        assertEquals(Optional.of("all"), params.warnings());
    }
}