package no.ion.modulec.compiler.multi;

import no.ion.modulec.compiler.CompilationResult;
import no.ion.modulec.compiler.ModulePath;
import no.ion.modulec.compiler.Release;
import no.ion.modulec.file.BasicAttributes;
import no.ion.modulec.file.Pathname;
import no.ion.modulec.file.TestDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static no.ion.modulec.util.Exceptions.uncheckIO;
import static no.ion.modulec.util.Exceptions.uncheckInterrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerTest {
    private final Compiler compiler = new Compiler();

    @TempDir
    Path tempDir;

    private Pathname workDir;

    @BeforeEach
    void setUp() {
        workDir = Pathname.of(tempDir);
        assertTrue(workDir.readAttributesIfExists(true).isPresent());
    }

    @Test
    void moduleName() {
        String content = """
                         @SuppressWarnings("module")
                         module m1 {
                         }
                         """;
        assertEquals("m1", Compiler.moduleNameOf("src/module-info.java", content, Release.ofJre()));
    }

    @Test
    void compileTrivialModule() {
        Optional<BasicAttributes> attributes = workDir.readAttributesIfExists(true);
        assertTrue(attributes.isPresent());
        assertTrue(attributes.get().isDirectory());

        var srcDir = workDir.resolve("src");

        srcDir.resolve("module-info.java")
              .makeParentDirectories()
              .writeUtf8("""
                         module no.ion.modulec.example {
                           exports no.ion.modulec.example;
                         }
                         """);

        srcDir.resolve("no/ion/modulec/example/Example.java")
              .makeParentDirectories()
              .writeUtf8("""
                         package no.ion.modulec.example;
                         class Example {
                         }
                         """);


        var destDir = workDir.resolve("target");

        var compilation = new MultiModuleCompilationAndPackaging(Release.ofJre());
        compilation.addModule()
                .addSourceDirectories(List.of(srcDir.path()))
                .setClassOutputDirectory(destDir.path());
        CompilationResult result = compiler.compile(compilation);

        Optional<BasicAttributes> moduleInfoClassAttributes = destDir.resolve("module-info.class").readAttributesIfExists(true);
        assertTrue(moduleInfoClassAttributes.isPresent());
        assertTrue(moduleInfoClassAttributes.get().isFile());

        String resultString = result.message();
        assertEquals("", resultString, "Bad result: " + resultString);
    }

    @Test
    void compileTwoModulesWithMultipleSourceDirectories() {
        TestDirectory.with(CompilerTest.class, workDir -> {
            makeTwoModulesWithMultiSources(workDir);

            var compilation = new MultiModuleCompilationAndPackaging(Release.ofJre());
            compilation.addModule()
                       .addSourceDirectories(List.of(workDir.resolve("moduleA/src1").path(), workDir.resolve("moduleA/src2").path()))
                       .setClassOutputDirectory(workDir.resolve("moduleA/target").path());
            compilation.addModule()
                    .addSourceDirectories(List.of(workDir.resolve("moduleB/src").path()))
                    .setClassOutputDirectory(workDir.resolve("moduleB/target").path());
            CompilationResult result = compiler.compile(compilation);
            assertTrue(result.success(), "Compilation failed: " + result.message());
            assertEquals("", result.message(), "Bad message: " + result.message());

            assertTrue(workDir.resolve("moduleA/target").readAttributesIfExists(true).map(BasicAttributes::isDirectory).orElse(false));
            assertTrue(workDir.resolve("moduleA/target/example/A/internal/Internal.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
            assertTrue(workDir.resolve("moduleA/target/module-info.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
            assertTrue(workDir.resolve("moduleA/target/example/A/A.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));

            assertTrue(workDir.resolve("moduleB/target").readAttributesIfExists(true).map(BasicAttributes::isDirectory).orElse(false), "moduleB/target does not exist");
            assertTrue(workDir.resolve("moduleB/target/example/B/Main.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
            assertTrue(workDir.resolve("moduleB/target/module-info.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));

            int returnCode = java(workDir, "-p", "moduleA/target:moduleB/target", "-m", "example.B/example.B.Main");
            assertEquals(42, returnCode);

            assertEquals(7, workDir.resolve("moduleA/target").deleteRecursively());
            assertEquals(5, workDir.resolve("moduleB/target").deleteRecursively());
        });
    }

    @Test
    void compileSequentially() {
        TestDirectory.with(CompilerTest.class, workDir -> {
            makeTwoModulesWithMultiSources(workDir);

            {
                var compilationA = new MultiModuleCompilationAndPackaging(Release.ofJre());
                compilationA.addModule()
                        .addSourceDirectories(List.of(workDir.resolve("moduleA/src1").path(), workDir.resolve("moduleA/src2").path()))
                        .setClassOutputDirectory(workDir.resolve("moduleA/target").path());
                CompilationResult resultA = compiler.compile(compilationA);
                assertTrue(resultA.success(), "Compilation failed: " + resultA.message());
                assertEquals("", resultA.message(), "Bad message: " + resultA.message());

                assertTrue(workDir.resolve("moduleA/target").readAttributesIfExists(true).map(BasicAttributes::isDirectory).orElse(false));
                assertTrue(workDir.resolve("moduleA/target/example/A/internal/Internal.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
                assertTrue(workDir.resolve("moduleA/target/module-info.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
                assertTrue(workDir.resolve("moduleA/target/example/A/A.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
            }

            {
                var compilationB = new MultiModuleCompilationAndPackaging(Release.ofJre());
                compilationB.setModulePath(new ModulePath().addExplodedModule(workDir.resolve("moduleA/target").path()));
                compilationB.addModule()
                            .addSourceDirectories(List.of(workDir.resolve("moduleB/src").path()))
                            .setClassOutputDirectory(workDir.resolve("moduleB/target").path());
                CompilationResult resultB = compiler.compile(compilationB);

                assertTrue(resultB.success(), "Compilation failed: " + resultB.message());
                assertEquals("", resultB.message(), "Bad message: " + resultB.message());

                assertTrue(workDir.resolve("moduleB/target").readAttributesIfExists(true).map(BasicAttributes::isDirectory).orElse(false), "moduleB/target does not exist");
                assertTrue(workDir.resolve("moduleB/target/example/B/Main.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
                assertTrue(workDir.resolve("moduleB/target/module-info.class").readAttributesIfExists(true).map(BasicAttributes::isFile).orElse(false));
            }

            int returnCode = java(workDir, "-p", "moduleA/target:moduleB/target", "-m", "example.B/example.B.Main");
            assertEquals(42, returnCode);

            assertEquals(7, workDir.resolve("moduleA/target").deleteRecursively());
            assertEquals(5, workDir.resolve("moduleB/target").deleteRecursively());
        });
    }

    /** Executes java with the given arguments.  Returns the exit code. */
    private static int java(Pathname currentWorkingDirectory, String... args) {
        String javaHomeProperty = System.getProperty("java.home");
        assertNotNull(javaHomeProperty, "java.home property is null");
        String java = Paths.get(javaHomeProperty, "bin", "java").toString();

        List<String> command = new ArrayList<>();
        command.add(java);
        Collections.addAll(command, args);

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(currentWorkingDirectory.file())
                .inheritIO();
        Process process = uncheckIO(processBuilder::start);
        return uncheckInterrupted(() -> process.waitFor());
    }

    private static void makeTwoModulesWithMultiSources(Pathname workDir) {
        workDir.resolve("moduleA/src1/module-info.java")
               .makeParentDirectories()
               .writeUtf8("""
                                  module example.A {
                                    exports example.A;
                                  }
                                  """);

        workDir.resolve("moduleA/src1/example/A/internal/Internal.java")
               .makeParentDirectories()
               .writeUtf8("""
                              package example.A.internal;
                              public class Internal {
                                public static int code = 42;
                              }
                              """);

        workDir.resolve("moduleA/src2/example/A/A.java")
               .makeParentDirectories()
               .writeUtf8("""
                              package example.A;
                              import example.A.internal.Internal;
                              public class A {
                                public static int getCode() { return Internal.code; }
                              }
                              """);

        workDir.resolve("moduleB/src/module-info.java")
               .makeParentDirectories()
               .writeUtf8("""
                                  module example.B {
                                    requires example.A;
                                  }
                                  """);

        workDir.resolve("moduleB/src/example/B/Main.java")
               .makeParentDirectories()
               .writeUtf8("""
                               package example.B;
                               import example.A.A;
                               public class Main {
                                   public static void main(String... args) {
                                       System.exit(A.getCode());
                                   }
                               }
                               """);
    }

    @Test
    void errorMessages() {
        Pathname src = workDir.resolve("src");
        Pathname classes = workDir.resolve("classes");

        var compilation = new MultiModuleCompilationAndPackaging(Release.ofJre());
        CompilationResult result = compiler.compile(compilation);
        // The "no source files" is not informative if no modules were specified
        //assertEquals("error: no source files", result.makeMessage());
        assertTrue(result.message().startsWith("error: no modules"), result.message());

        compilation.addModule()
                   .addSourceDirectories(List.of(src.path()))
                   .setClassOutputDirectory(classes.path());
        src.makeDirectories();
        result = compiler.compile(compilation);
        assertTrue(result.message().startsWith("error: no source files found in /"), result.message());

        src.resolve("module-info.java")
           .writeUtf8("""
                         module a.example {
                           exports a.example.api;
                         }
                         """);
        result = compiler.compile(compilation);
        String message = result.message();
        assertTrue(message.contains("""
                             src/module-info.java:2: error: package is empty or does not exist: a.example.api
                               exports a.example.api;
                                                ^
                             1 error
                             """),
                   "Bad message: " + message);
    }
}