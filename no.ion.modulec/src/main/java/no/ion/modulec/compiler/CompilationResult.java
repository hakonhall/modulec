package no.ion.modulec.compiler;

import no.ion.modulec.ResultType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public record CompilationResult(ResultType resultType, int sourceFiles, Duration duration, String message, Path destination) {
    public CompilationResult {
        Objects.requireNonNull(resultType, "resultType cannot be null");
        Objects.requireNonNull(duration, "duration cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        // In multi-module compilation, there is one destination directory per module and null is passed here.
        //Objects.requireNonNull(destination, "destination cannot be null");
    }

    public static CompilationResult ofError(long startNanos, String message) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new CompilationResult(ResultType.ERROR, -1, duration, message, null);
    }

    public static CompilationResult of(boolean success, int sourceFiles, long startNanos, List<Diagnostic> diagnostics,
                                       String out, Path destination, RuntimeException exception) {
        return of(success, sourceFiles, startNanos, makeMessage(diagnostics, out, exception), destination);
    }

    public static CompilationResult of(boolean success, int sourceFiles, long startNanos, String message, Path destination) {
        ResultType resultType = success ? ResultType.OK : ResultType.ERROR;
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new CompilationResult(resultType, sourceFiles, duration, message, destination);
    }

    public static CompilationResult ofNoop(int sourceFiles, long startNanos, Path destination) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new CompilationResult(ResultType.NOOP, sourceFiles, duration, "", destination);
    }

    public boolean success() { return resultType().success(); }
    public boolean noop() { return resultType == ResultType.NOOP; }

    /** Tries to make a message similar to that produced by the javac tool. */
    private static String makeMessage(List<Diagnostic> diagnostics, String out, RuntimeException exception) {
        if (exception != null) {
            return exception.getMessage();
        }

        int errors = 0;
        int warnings = 0;

        var buffer = new StringBuilder();
        for (var diagnostic : diagnostics) {
            switch (diagnostic.kind()) {
                case ERROR -> errors++;
                case WARNING, MANDATORY_WARNING -> warnings++;
            }

            if (diagnostic.source().isPresent() &&
                // E.g. the message "error: warnings found and -Werror specified" comes in a diagnostic
                // with a source (the last processed?), but no line number.
                (diagnostic.lineNumber().isPresent() ||
                 // On the other hand, the message "junit5/mod/junit-jupiter-api-5.9.1.jar(/org/junit/jupiter/api/Test.class):
                 // warning: Cannot find annotation method 'status()' in type 'API': class file for org.apiguardian.api.API not found"
                 // has location but is a warning.
                 diagnostic.kind() == javax.tools.Diagnostic.Kind.WARNING)) {

                buffer.append(diagnostic.source().get().getName())
                      .append(':');
                diagnostic.lineNumber().ifPresent(lineNumber -> buffer.append(lineNumber)
                                                                      .append(':'));
                buffer.append(' ');
            }

            if (diagnostic.kind() == javax.tools.Diagnostic.Kind.ERROR) {
                buffer.append("error: ");
            } else if (diagnostic.kind() == javax.tools.Diagnostic.Kind.WARNING ||
                       diagnostic.kind() == javax.tools.Diagnostic.Kind.MANDATORY_WARNING) {
                buffer.append("warning: ");
                diagnostic.code().ifPresent(code -> {
                    // TODO: this is printed to hint of SuppressWarnings, but not otherwise.
                    buffer.append('[').append(code).append("] ");
                    int lastDot = code.lastIndexOf('.');
                    if (lastDot >= 0) {
                        code = code.substring(lastDot + 1);
                    }
                    //buffer.append('[').append(code).append("] ");
                });
            }
            // A message WITH a newline is split in two, with 2 lines pointing to the code line and column
            // in between.
            String message = diagnostic.message();
            int newlineIndex = message.indexOf('\n');
            buffer.append(newlineIndex >= 0 ? message.substring(0, newlineIndex + 1) : message + '\n');

            diagnostic.lineNumber().ifPresent(lineno -> {
                CharSequence charSequence = uncheckIO(() -> diagnostic.source().get().getCharContent(true));
                if (charSequence != null) {
                    String content = String.valueOf(charSequence);
                    String[] lines = content.lines().toArray(String[]::new);
                    if (lineno - 1 >= 0 && lineno - 1 < lines.length) {
                        String line = lines[(int) (lineno - 1)];
                        buffer.append(line).append('\n');
                        diagnostic.columnNumber().ifPresent(columnNumber -> {
                            if (columnNumber > 1)
                                buffer.append(" ".repeat((int) (columnNumber - 1)));
                            buffer.append("^\n");
                        });
                    }
                }
            });

            if (newlineIndex >= 0)
                buffer.append(message.substring(newlineIndex + 1)).append('\n');
        }

        if (errors > 0)
            buffer.append(errors).append(errors == 1 ? " error\n" : " errors\n");
        if (warnings > 0)
            buffer.append(warnings).append(warnings == 1 ? " warning\n" : " warnings\n");

        if (!out.isEmpty())
            buffer.append(out);

        return buffer.toString();
    }
}
