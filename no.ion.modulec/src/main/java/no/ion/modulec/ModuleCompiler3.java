package no.ion.modulec;

import no.ion.modulec.compiler.single.ModuleCompiler;
import no.ion.modulec.modco.ProgramContext;
import no.ion.modulec.modco.Options;

public class ModuleCompiler3 {
    private static final String PROGRAM_NAME = "modco";

    private final ProgramContext context = new ProgramContext();
    private final ModuleCompiler moduleCompiler = new ModuleCompiler(context);

    private ModuleCompiler3() {}

    public static void main(String... args) {
        new ModuleCompiler3().run(args);
    }

    public void run(String... args) {
        try {
            Options options = Options.parse(context, args);
            moduleCompiler.make(options.params());
        } catch (UsageException e) {
            context.log().info(e.getMessage());
            System.exit(0);
        } catch (UserErrorException e) {
            context.log().infoLine(e.getMessage() + ", see '--help' for usage");
            System.exit(1);
        } catch (ModuleCompilerException e) {
            if (!e.isSilent()) {
                if (e.isMultiLine()) {
                    context.log().info(e.getMessage());
                } else {
                    context.log().infoLine(PROGRAM_NAME + ": " + e.getMessage());
                }
            }
            System.exit(1);
        }

        System.exit(0);
    }
}
