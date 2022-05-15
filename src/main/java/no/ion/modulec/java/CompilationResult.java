package no.ion.modulec.java;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public record CompilationResult(boolean success, Duration duration, List<Diagnostic> diagnostics, String out,
                                RuntimeException exception) {
    public CompilationResult {
        Objects.requireNonNull(duration, "duration cannot be null");
        Objects.requireNonNull(duration, "diagnostics cannot be null");
        Objects.requireNonNull(duration, "out cannot be null");
    }

    public static CompilationResult ofError(long startNanos, String message) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new CompilationResult(false, duration, List.of(), message, null);
    }

    /**
     * Tries to make a message similar to that produced by the javac tool.
     */
    public String makeMessage() {
        if (exception != null) {
            return exception.getMessage();
        }

        int errors = 0;

        var buffer = new StringBuilder();
        for (var diagnostic : diagnostics) {
            if (diagnostic.kind() == javax.tools.Diagnostic.Kind.ERROR) {
                errors += 1;
            }

            if (diagnostic.source().isPresent()) {
                buffer.append(diagnostic.source().get().getName());
                diagnostic.lineNumber().ifPresent(lineNumber -> buffer.append(':').append(lineNumber));
                buffer.append(": ");
                if (diagnostic.kind() == javax.tools.Diagnostic.Kind.ERROR) {
                    buffer.append("error: ");
                } else if (diagnostic.kind() == javax.tools.Diagnostic.Kind.WARNING || diagnostic.kind() == javax.tools.Diagnostic.Kind.MANDATORY_WARNING) {
                    buffer.append("warning: ");
                }
                buffer.append(diagnostic.message()).append('\n');

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
            }
        }

        if (errors > 0) {
            buffer.append(errors).append(errors == 1 ? " error\n" : " errors\n");
        }

        if (!out.isEmpty())
            buffer.append(out);

        buffer.append(success ? "OK\n" : "FAILED\n");
        buffer.append(String.format("Completed in %.3fs\n", duration.toNanos() / 1000_000.0));
        return buffer.toString();
    }
}
