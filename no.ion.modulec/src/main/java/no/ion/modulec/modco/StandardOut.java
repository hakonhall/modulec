package no.ion.modulec.modco;

import no.ion.modulec.MessageSink;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class StandardOut implements MessageSink {
    private final BooleanSupplier showLog;
    private final BooleanSupplier showDebug;
    private final BooleanSupplier showMilestones;
    private final BooleanSupplier showCommands;

    public StandardOut(BooleanSupplier showLog,
                       BooleanSupplier showDebug,
                       BooleanSupplier showMilestones,
                       BooleanSupplier showCommands) {
        this.showLog = showLog;
        this.showDebug = showDebug;
        this.showMilestones = showMilestones;
        this.showCommands = showCommands;
    }

    @Override
    public void log(Type type, Supplier<String> message) {
        if (enabled(type))
            System.out.print(message.get());
    }

    private boolean enabled(Type type) {
        return switch (type) {
            case COMMAND -> showCommands.getAsBoolean();
            case DEBUG -> showDebug.getAsBoolean();
            case INFO -> showLog.getAsBoolean();
            case MILESTONE -> showMilestones.getAsBoolean();
        };
    }
}
