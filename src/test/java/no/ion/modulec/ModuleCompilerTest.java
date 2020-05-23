package no.ion.modulec;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import no.ion.modulec.ModuleCompiler.ModuleCompilerException;
import org.junit.jupiter.api.Test;

import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.spi.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ModuleCompilerTest {
    private final FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    private final ModuleCompiler moduleCompiler = ModuleCompiler.create();

    @Test
    void verifyModuleName() throws IOException {
        moduleCompiler.getModuleName(Path.of("src/test/resources/module-info-valid.java"));

        try {
            moduleCompiler.getModuleName(Path.of("src/test/resources/module-info-invalid.java"));
            fail();
        } catch (ModuleCompilerException e) {
            assertEquals("error: invalid module declaration in src/test/resources/module-info-invalid.java", e.getMessage());
        }
    }

    @Test
    void verifyOptionParsing() {
        String[] args = {"-f", "foo", "-g"};
        assertTrue(ModuleCompiler.isOption(args, 0, "-f"));
        assertTrue(ModuleCompiler.isOption(args, 0, "-f", "--file"));
        assertTrue(ModuleCompiler.isOption(args, 0, "--file", "-f"));
        assertFalse(ModuleCompiler.isOption(args, 1, "-f"));
        assertFalse(ModuleCompiler.isOption(args, 0, "-g"));

        assertTrue(ModuleCompiler.isOptionWithArgument(args, 0, "-f"));
        assertTrue(ModuleCompiler.isOptionWithArgument(args, 0, "-f", "--file"));
        assertTrue(ModuleCompiler.isOptionWithArgument(args, 0, "--file", "-f"));
        assertFalse(ModuleCompiler.isOptionWithArgument(args, 1, "-f"));
        assertFalse(ModuleCompiler.isOptionWithArgument(args, 0, "-g"));

        assertFalse(ModuleCompiler.isOptionWithArgument(args, 2, "-h"));
        try {
            ModuleCompiler.isOptionWithArgument(args, 2, "-g");
            fail();
        } catch (ModuleCompiler.ModuleCompilerException e) {
            assertEquals("error: -g requires an argument", e.getMessage());
        }
    }

    @Test
    void verifyHelpOutput() throws IOException {
        String expectedHelpText = Files.readString(Path.of("src/test/resources/help.txt"));
        assertEquals(expectedHelpText, ModuleCompiler.getHelpText());
        assertEquals(expectedHelpText, ModuleCompiler.mainApi("--help").diagnostics().get());
        assertEquals(expectedHelpText, ModuleCompiler.mainApi("-h").diagnostics().get());
    }

    @Test
    void verifyJavacAndJarArguments() throws IOException {
        Path root = fileSystem.getPath("/project");
        Path sourcePath = root.resolve("src");
        Path moduleInfoPath = sourcePath.resolve("module-info.java");
        Path outputDirectory = root.resolve("target");
        ModuleDescriptor.Version version = ModuleDescriptor.Version.parse("1.2.3");
        Path manifestPath = root.resolve("manifest.mf");
        Path resourceDirectory1 = root.resolve("rsrc1");
        Path resourceDirectory2 = root.resolve("rsrc2");
        Path jarPath = root.resolve("foo.jar");

        ModuleCompiler.Options options = new ModuleCompiler.Options()
                .setFileSystem(fileSystem)
                .setOutputDirectory(outputDirectory)
                .addJavacArguments("javacarg1", "javacarg2")
                .addResourceDirectories(resourceDirectory1, resourceDirectory2)
                .setJarPath(jarPath)
                .setMainClass("no.ion.modulec.test.Main")
                .setManifestPath(manifestPath)
                .setPath("a:b")
                .setSourceDirectory(sourcePath)
                .setVersion(version);

        Files.createDirectories(moduleInfoPath.getParent());
        Files.writeString(moduleInfoPath, "");
        Files.createDirectory(resourceDirectory1);
        Files.createDirectory(resourceDirectory2);
        Files.writeString(manifestPath, "");

        options.validate();

        var javaCompiler = new MockJavaCompiler();
        var jarTool = new MockJarTool();
        var mockedModuleCompiler = new MockModuleCompiler(javaCompiler, jarTool, "no.ion.modulec.test");

        javaCompiler.expectRun(0,
                "-p", "a:b",
                "--module-source-path", "/project/target/javac-src",
                "-m", "no.ion.modulec.test",
                "--module-version", "1.2.3",
                "-d", "/project/target/javac-classes",
                "javacarg1", "javacarg2");

        jarTool.expectRun(0,
                "-c",
                "-f", "/project/foo.jar",
                "-m", "/project/manifest.mf",
                "-e", "no.ion.modulec.test.Main",
                "-C", "/project/target/classes", ".",
                "-C", "/project/rsrc1", ".",
                "-C", "/project/rsrc2", ".");

        ModuleCompiler.SuccessResult result = mockedModuleCompiler.make(options);
        Optional<String> diagnostics = result.diagnostics();
        assertTrue(diagnostics.isEmpty(), diagnostics::get);

        options.setDryRun(true);
        ModuleCompiler.SuccessResult dryRunResult = mockedModuleCompiler.make(options);
        Optional<String> dryRunDiagnostics = dryRunResult.diagnostics();
        assertTrue(dryRunDiagnostics.isPresent());
        assertEquals(
                "javac -p a:b --module-source-path /project/target/javac-src -m no.ion.modulec.test " +
                        "--module-version 1.2.3 -d /project/target/javac-classes javacarg1 javacarg2\n" +
                "jar -c -f /project/foo.jar -m /project/manifest.mf -e no.ion.modulec.test.Main " +
                        "-C /project/target/classes . -C /project/rsrc1 . -C /project/rsrc2 .\n",
                dryRunDiagnostics.get());

    }

    private static class MockModuleCompiler extends ModuleCompiler {
        private final String moduleName;

        MockModuleCompiler(JavaCompiler javaCompiler, ToolProvider jarTool, String moduleName) {
            super(javaCompiler, jarTool);
            this.moduleName = moduleName;
        }

        @Override
        String getModuleName(Path moduleInfoPath) throws IOException {
            return moduleName;
        }
    }

    private static class MockJavaCompiler implements JavaCompiler {
        private int returnValue;
        private String[] expectedArgs;

        public void expectRun(int returnValue, String... expectedArgs) {
            this.returnValue = returnValue;
            this.expectedArgs = expectedArgs;
        }

        @Override
        public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
            assertEquals(List.of(expectedArgs), List.of(arguments));
            return returnValue;
        }

        // rest is unsupported

        @Override
        public CompilationTask getTask(Writer out, JavaFileManager fileManager, DiagnosticListener<? super JavaFileObject> diagnosticListener, Iterable<String> options, Iterable<String> classes, Iterable<? extends JavaFileObject> compilationUnits) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StandardJavaFileManager getStandardFileManager(DiagnosticListener<? super JavaFileObject> diagnosticListener, Locale locale, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int isSupportedOption(String option) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<SourceVersion> getSourceVersions() {
            throw new UnsupportedOperationException();
        }
    }

    private static class MockJarTool implements ToolProvider {
        private int returnValue;
        private String[] expectedArgs;

        public void expectRun(int returnValue, String... expectedArgs) {
            this.returnValue = returnValue;
            this.expectedArgs = expectedArgs;
        }

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            assertEquals(List.of(expectedArgs), List.of(args));
            return returnValue;
        }
    }
}