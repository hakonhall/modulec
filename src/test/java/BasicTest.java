import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicTest {
    private final Path basicPath = Path.of("src/test/resources/basic");
    private final modulec modulec = new modulec(FileSystems.getDefault());

    @Test
    void verifyBasicCompile() throws IOException  {
        int exitCode = modulec.noExitMain(
                "-f", basicPath.resolve("target").toString() + '/',
                "-v", "1.0.0",
                "-d", basicPath.resolve("target/classes").toString(),
                basicPath.resolve("src").toString());
        assertEquals(0, exitCode);
        assertTrue(Files.exists(basicPath.resolve("target/no.ion.tst-1.0.0.jar")));
        assertTrue(Files.exists(basicPath.resolve("target/classes/module-info.class")));
        assertTrue(Files.exists(basicPath.resolve("target/classes/no/ion/tst1/Exported.class")));
        assertFalse(Files.exists(basicPath.resolve("target/classes/.src")));
        assertFalse(Files.exists(basicPath.resolve("target/classes/.classes")));
    }
}
