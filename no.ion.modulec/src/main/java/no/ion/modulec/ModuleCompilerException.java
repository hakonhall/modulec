package no.ion.modulec;

public class ModuleCompilerException extends RuntimeException {
    private boolean multiLine = false;
    private boolean silent = false;

    public ModuleCompilerException(String message) {
        super(message);
    }

    public ModuleCompilerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModuleCompilerException setMultiLine(boolean multiLine) {
        this.multiLine = multiLine;
        return this;
    }

    public ModuleCompilerException setSilent(boolean silent) {
        this.silent = silent;
        return this;
    }

    public boolean isMultiLine() { return multiLine; }
    public boolean isSilent() { return silent; }

}
