package no.ion.modulec;

public class ModuleCompilerException extends RuntimeException {
    public ModuleCompilerException(String message) {
        super(message);
    }

    public ModuleCompilerException(String message, Throwable cause) {
        super(message, cause);
    }
}