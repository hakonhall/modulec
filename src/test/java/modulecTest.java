import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import static org.junit.jupiter.api.Assertions.assertEquals;

class modulecTest {
    private final FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    private final modulec modulec = new modulec(fileSystem);

    @Test
    void verifyBasicCompile() {
    }
}