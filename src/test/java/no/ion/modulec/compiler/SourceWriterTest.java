package no.ion.modulec.compiler;

import no.ion.modulec.file.TestDirectory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceWriterTest {
    @Test
    void verifyCorrectWrites() {
        TestDirectory.with(SourceWriterTest.class, workDirectory -> {
            var writer = SourceWriter.rootedAt(workDirectory);
            String moduleInfo = """
                    module no.ion.example {
                      exports no.ion.foo;
                    }
                    """;
            String classFile = """
                    package no.ion.foo;
                    import no.ion.bar;
                    public class Example {
                    }
                    """;

            writer.writeModuleInfoJava(moduleInfo)
                  .writeClass(classFile);

            assertEquals(moduleInfo, workDirectory.resolve("module-info.java").readUtf8());
            assertEquals(classFile, workDirectory.resolve("no/ion/foo/Example.java").readUtf8());
        });
    }
}