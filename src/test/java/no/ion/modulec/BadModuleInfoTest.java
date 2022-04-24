package no.ion.modulec;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class BadModuleInfoTest {
    @Test
    void verifyModuleCompilerDiagnostics() {
        var  compiler = ModuleCompiler.create();

        Path root = Path.of("src/test/resources/bad-module-info");

        var options = new ModuleCompiler.Options()
                .setSourceDirectory(root.resolve("src"))
                .setOutputDirectory(root.resolve("target"));

        try {
            compiler.make(options);
            fail();
        } catch (ModuleCompilerException e) {
            assertEquals("src/test/resources/bad-module-info/src/module-info.java:2: error: package is empty or does not exist: a.b\n" +
                         "    exports a.b;\n" +
                         "             ^\n" +
                         "src/test/resources/bad-module-info/src/module-info.java:3: error: package is empty or does not exist: c.d\n" +
                         "    exports c.d;\n" +
                         "             ^\n" +
                         "src/test/resources/bad-module-info/src/module-info.java:1: warning: [module] module name component tst1 should avoid terminal digits\n" +
                         "module no.ion.tst1 {\n" +
                         "             ^\n" +
                         "2 errors\n" +
                         "1 warning\n",
                         e.getMessage());
        }
    }
}
