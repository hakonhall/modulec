package no.ion.modulec;

import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.modco.Options;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

public class ModuleCompiler3 {
    private static final String PROGRAM_NAME = "modco";

    private final FileSystem fileSystem;
    private final ModuleCompiler moduleCompiler;

    private ModuleCompiler3(FileSystem fileSystem, ModuleCompiler moduleCompiler) {
        this.fileSystem = fileSystem;
        this.moduleCompiler = moduleCompiler;
    }

    public static void main(String... args) {
        ModuleCompiler3 compiler = new ModuleCompiler3(FileSystems.getDefault(), new ModuleCompiler());
        try {
            compiler.run(args);
        } catch (UsageException e) {
            System.out.print(e.getMessage());
            System.exit(0);
        } catch (UserErrorException e) {
            System.err.println(e.getMessage() + ", see '--help' for usage");
            System.exit(1);
        } catch (ModuleCompilerException e) {
            if (!e.isSilent()) {
                if (e.isMultiLine()) {
                    System.err.print(e.getMessage());
                } else {
                    System.err.println(PROGRAM_NAME + ": " + e.getMessage());
                }
            }
            System.exit(1);
        }

        System.exit(0);
    }

    public void run(String... args) {
        Options options = Options.parse(fileSystem, args);
        moduleCompiler.make(options.params());
    }
}
