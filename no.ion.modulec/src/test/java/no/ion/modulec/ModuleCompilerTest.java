package no.ion.modulec;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import javax.tools.JavaCompiler;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
class ModuleCompilerTest {
    private final FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

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
        } catch (ModuleCompilerException e) {
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
        Path javaSourcePath1 = sourcePath.resolve("no/Foo.java");
        Path javaSourcePath2 = sourcePath.resolve("no/Bar.java");
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

        Files.createDirectories(javaSourcePath1.getParent());
        Files.writeString(javaSourcePath1, "");
        Files.writeString(javaSourcePath2, "");
        Files.writeString(moduleInfoPath, "");
        Files.createDirectory(resourceDirectory1);
        Files.createDirectory(resourceDirectory2);
        Files.writeString(manifestPath, "");

        options.validate();

        var jarTool = new MockJarTool();
        var mockedModuleCompiler = new MockModuleCompiler(null, jarTool);
        mockedModuleCompiler.setModuleName("no.ion.modulec.test");
        mockedModuleCompiler.expectCompile(
                List.of("-p", "a:b",
                        "--module-version", "1.2.3",
                        "-d", "/project/target/classes",
                        "javacarg1", "javacarg2"),
                List.of(moduleInfoPath, javaSourcePath1, javaSourcePath2),
                () -> new ModuleCompiler.SuccessResult(""));

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
                "javac -p a:b --module-version 1.2.3 -d /project/target/classes javacarg1 javacarg2 " +
                        "/project/src/module-info.java /project/src/no/Bar.java /project/src/no/Foo.java\n" +
                "jar -c -f /project/foo.jar -m /project/manifest.mf -e no.ion.modulec.test.Main " +
                        "-C /project/target/classes . -C /project/rsrc1 . -C /project/rsrc2 .\n",
                dryRunDiagnostics.get());

    }

    private static class MockModuleCompiler extends ModuleCompiler {
        private String moduleName = null;
        private List<String> expectedJavacOptions = null;
        private List<Path> expectedJavacSourceFiles = null;
        private Supplier<SuccessResult> resultSupplier = null;

        MockModuleCompiler(JavaCompiler javaCompiler, ToolProvider jarTool) { super(javaCompiler, jarTool); }

        public void setModuleName(String moduleName) { this.moduleName = moduleName; }

        @Override
        final protected String getModuleName(Path moduleInfoPath) {
            return moduleName == null ? super.getModuleName(moduleInfoPath) : moduleName;
        }

        public void expectCompile(List<String> expectedJavacOptions,
                                  List<Path> expectedJavacSourceFiles,
                                  Supplier<SuccessResult> resultSupplier) {
            this.expectedJavacOptions = expectedJavacOptions;
            this.expectedJavacSourceFiles = expectedJavacSourceFiles;
            this.resultSupplier = resultSupplier;
        }

        @Override
        protected SuccessResult compile(List<String> javacOptions, List<Path> javacSourceFiles) throws ModuleCompilerException {
            if (expectedJavacOptions != null) {
                assertEquals(expectedJavacOptions, javacOptions);
            }

            if (expectedJavacSourceFiles != null) {
                assertEquals(
                        expectedJavacSourceFiles.stream().sorted().collect(toList()),
                        javacSourceFiles.stream().sorted().collect(toList()));
            }

            return resultSupplier == null ? super.compile(javacOptions, javacSourceFiles) : resultSupplier.get();
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
        public int run(PrintWriter out, PrintWriter err, String... args) {
            assertEquals(List.of(expectedArgs), List.of(args));
            return returnValue;
        }

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }
    }

}
