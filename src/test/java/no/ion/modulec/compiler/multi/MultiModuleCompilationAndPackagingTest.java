package no.ion.modulec.compiler.multi;

import no.ion.modulec.compiler.Release;
import no.ion.modulec.compiler.SourceWriter;
import no.ion.modulec.file.FileMode;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.file.TemporaryDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.module.ModuleDescriptor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiModuleCompilationAndPackagingTest {
    private final TemporaryDirectory temporaryDirectory = Pathname.makeTmpdir(MultiModuleCompilationAndPackagingTest.class.getName() + ".", "", FileMode.fromModeInt(0777));
    private final Pathname workDirectory = temporaryDirectory.directory();
    private final SourceWriter srcA = SourceWriter.rootedAt(workDirectory.resolve("srcA"));
    private final SourceWriter srcB = SourceWriter.rootedAt(workDirectory.resolve("srcB"));
    private final SourceWriter srcC = SourceWriter.rootedAt(workDirectory.resolve("srcC"));
    private final MultiModuleCompiler compiler = new MultiModuleCompiler();

    @AfterEach
    void tearDown() {
        temporaryDirectory.close();
    }

    @Test
    void basics() {
        srcA.writeModuleInfoJava("""
                                        module no.ion.exampleA {
                                          exports no.ion.a;
                                        }
                                        """)
            .writeClass("""
                                    package no.ion.a;
                                    public class A {
                                      public static final String a = "a";
                                      public static void main(String... args) {}
                                    }
                                    """);

        var compilation = new MultiModuleCompilationAndPackaging(Release.ofJre())
                .setBuildDirectory(workDirectory.resolve("out").path());
        compilation.addModule()
                   .addSourceDirectories(List.of(srcA.path()))
                   .setMainClass("no.ion.a.A")
                   .setVersion(ModuleDescriptor.Version.parse("1.2.3"));

        MultiModuleCompilationAndPackagingResult result = compiler.make(compilation);

        assertTrue(result.cResult().makeMessage().startsWith("OK\n"), "Unexpected message: " + result.cResult().makeMessage());
        assertTrue(result.cResult().success());
        assertEquals("", result.cResult().out());
        assertNull(result.cResult().exception());

        assertTrue(workDirectory.resolve("out").isDirectory());
        assertTrue(workDirectory.resolve("out/no.ion.exampleA").isDirectory());
        assertTrue(workDirectory.resolve("out/no.ion.exampleA/no.ion.exampleA-1.2.3.jar").isFile());
    }

    @Test
    void twoModules() {
        srcA.writeModuleInfoJava("""
                                        module no.ion.exampleA {
                                          exports no.ion.a;
                                        }
                                        """)
            .writeClass("""
                                    package no.ion.a;
                                    public class A {
                                      public static final int a = 1;
                                    }
                                    """);

        srcB.writeModuleInfoJava("""
                                         module no.ion.exampleB {
                                           requires no.ion.exampleA;
                                           exports no.ion.b;
                                         }
                                         """)
                .writeClass("""
                                    package no.ion.b;
                                    import no.ion.a.A;
                                    public class B {
                                      public static int foo() {
                                        return A.a;
                                      }
                                    }
                                    """);

        var compilation = new MultiModuleCompilationAndPackaging(Release.ofJre())
                .setBuildDirectory(workDirectory.resolve("out").path());
        compilation.addModule()
                   .addSourceDirectories(List.of(srcA.path()));
        compilation.addModule()
                .addSourceDirectories(List.of(srcB.path()));

        MultiModuleCompilationAndPackagingResult result = compiler.make(compilation);

        assertTrue(result.cResult().makeMessage().startsWith("OK\n"), "Unexpected message: " + result.cResult().makeMessage());
        assertTrue(result.cResult().success());
        assertEquals("", result.cResult().out());
        assertNull(result.cResult().exception());
    }
}
