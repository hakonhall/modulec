package no.ion.modulec;

public class UserErrorException extends ModuleCompilerException {
    public UserErrorException(String message) { super(message); }
    public UserErrorException(String message, Throwable cause) { super(message, cause); }
}
