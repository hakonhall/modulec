package no.ion.modulec;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BasicTest {
    private final FileSystem fileSystem = FileSystems.getDefault();
    private final Path basicPath = fileSystem.getPath("src/test/resources/basic");
    private final Path targetPath = basicPath.resolve("target");
    private final String[] argsForBasic = {
            "--output", basicPath.resolve("target").toString(),
            "--version", "1.2.3",
            "--main-class", "no.ion.tst1.Exported",
            "--path", ".",
            "--manifest", basicPath.resolve("manifest.mf").toString(),
            basicPath.resolve("src").toString()
    };

    @Test
    void verifyCommandLineArgumentParsing() {
        assertInvalidOptions("error: no source directory");
        assertInvalidOptions("error: no module-info.java in source directory", ".");
        assertValidOptions("src/test/resources/basic/src");

        assertInvalidOptions("error: missing manifest file: src/test/resources/basic/manifest.m",
                "--manifest", "src/test/resources/basic/manifest.m", "src/test/resources/basic/src");
        assertValidOptions(
                "--manifest", "src/test/resources/basic/manifest.mf", "src/test/resources/basic/src");

        assertInvalidOptions("error: invalid version: a b c: Version string does not start with a number",
                "--version", "a b c", "src/test/resources/basic/src");
        assertValidOptions(
                "--version", "1.2.3", "src/test/resources/basic/src");

        assertInvalidOptions("error: missing resource directory: doesnotexist",
                "-C", "doesnotexist", "src/test/resources/basic/src");
        assertValidOptions(
                "-C", ".", "src/test/resources/basic/src");

        assertValidOptions(argsForBasic);
    }

    private void assertValidOptions(String... args) {
        ModuleCompiler.Options options = ModuleCompiler.parseProgramArguments(fileSystem, args);
        options.validate();
    }

    private void assertInvalidOptions(String expectedExceptionMessage, String... args) {
        try {
            ModuleCompiler.Options options = ModuleCompiler.parseProgramArguments(fileSystem, args);
            options.validate();
            fail();
        } catch (ModuleCompilerException e) {
            assertEquals(expectedExceptionMessage, e.getMessage());
        }
    }

    @Test
    void verifyNoExitMain() {
        if (Files.exists(targetPath)) {
            deleteRecursively(targetPath.toFile());
        }

        verifyBasicIsBuilt(() -> {
            ModuleCompiler.SuccessResult result = ModuleCompiler.mainApi(argsForBasic);
            Optional<String> diagnostics = result.diagnostics();
            assertTrue(diagnostics.isEmpty(), diagnostics::get);
        });
    }

    @Test
    void verifyMake() {
        if (Files.exists(targetPath)) {
            deleteRecursively(targetPath.toFile());
        }

        var options = new ModuleCompiler.Options()
                .setOutputDirectory(basicPath.resolve("target"))
                .setVersion(ModuleDescriptor.Version.parse("1.2.3"))
                .setMainClass("no.ion.tst1.Exported")
                .setPath(".")
                .setManifestPath(basicPath.resolve("manifest.mf"))
                .setSourceDirectory(basicPath.resolve("src"));

        verifyBasicIsBuilt(() -> {
            ModuleCompiler.SuccessResult result = ModuleCompiler.create().make(options);
            Optional<String> diagnostics = result.diagnostics();
            assertTrue(diagnostics.isEmpty(), diagnostics::get);
        });
    }

    private void verifyBasicIsBuilt(Runnable runnable) {
        assertPathDoesNotExist("target");

        runnable.run();

        assertDirectory("target");
        assertDirectory("target/classes");
        assertRegularFile("target/classes/module-info.class");
        assertRegularFile("target/classes/no/ion/tst1/Exported.class");
        assertRegularFile("target/no.ion.tst-1.2.3.jar");
    }

    private void assertPathDoesNotExist(String pathRelativeBasic) {
        assertFalse(Files.exists(basicPath.resolve(pathRelativeBasic)));
    }

    private void assertDirectory(String pathRelativeBasic) {
        assertTrue(Files.isDirectory(basicPath.resolve(pathRelativeBasic)));
    }

    private void assertRegularFile(String pathRelativeBasic) {
        assertTrue(Files.isRegularFile(basicPath.resolve(pathRelativeBasic)));
    }

    private void deleteRecursively(File existingFile) {
        if (!Files.isSymbolicLink(existingFile.toPath())) {
            File[] directoryFiles = existingFile.listFiles();
            if (directoryFiles != null) {
                for (File directoryFile : directoryFiles) {
                    deleteRecursively(directoryFile);
                }
            }
        }

        existingFile.delete();
    }
}
