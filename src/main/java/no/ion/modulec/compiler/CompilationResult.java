package no.ion.modulec.compiler;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public record CompilationResult(boolean success, Duration duration, List<Diagnostic> diagnostics, String out,
                                Path destination, RuntimeException exception) {
    public CompilationResult {
        Objects.requireNonNull(duration, "duration cannot be null");
        Objects.requireNonNull(diagnostics, "diagnostics cannot be null");
        Objects.requireNonNull(out, "out cannot be null");
    }

    public static CompilationResult ofError(long startNanos, String message) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new CompilationResult(false, duration, List.of(), message, null, null);
    }

    /**
     * Tries to make a message similar to that produced by the javac tool.
     */
    public String makeMessage() {
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

            if (diagnostic.source().isPresent()) {
                diagnostic.lineNumber().ifPresent(lineNumber -> {
                    // E.g. the message "error: warnings found and -Werror specified" comes in a diagnostic
                    // with a source (the last processed?), but no line number.
                    buffer.append(diagnostic.source().get().getName());
                    buffer.append(':').append(lineNumber);
                    buffer.append(": ");
                });
                if (diagnostic.kind() == javax.tools.Diagnostic.Kind.ERROR) {
                    buffer.append("error: ");
                } else if (diagnostic.kind() == javax.tools.Diagnostic.Kind.WARNING ||
                           diagnostic.kind() == javax.tools.Diagnostic.Kind.MANDATORY_WARNING) {
                    buffer.append("warning: ");
                    diagnostic.code().ifPresent(code -> {
                        int lastDot = code.lastIndexOf('.');
                        if (lastDot >= 0) {
                            code = code.substring(lastDot + 1);
                        }
                        buffer.append('[').append(code).append("] ");
                    });
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

        if (errors > 0)
            buffer.append(errors).append(errors == 1 ? " error\n" : " errors\n");
        if (warnings > 0)
            buffer.append(warnings).append(warnings == 1 ? " warning\n" : " warnings\n");

        if (!out.isEmpty())
            buffer.append(out);

        return buffer.toString();
    }
}
