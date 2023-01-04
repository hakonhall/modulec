package no.ion.modulec.compiler;

import javax.tools.JavaFileObject;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record Diagnostic(javax.tools.Diagnostic.Kind kind,
                         Optional<JavaFileObject> source,
                         OptionalLong position,
                         OptionalLong startPosition,
                         OptionalLong endPosition,
                         OptionalLong lineNumber,
                         OptionalLong columnNumber,
                         Optional<String> code,
                         String message) {
    public Diagnostic {
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(position, "position cannot be null");
        Objects.requireNonNull(startPosition, "startPosition cannot be null");
        Objects.requireNonNull(endPosition, "endPosition cannot be null");
        Objects.requireNonNull(lineNumber, "lineNumber cannot be null");
        Objects.requireNonNull(columnNumber, "columnNumber cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }
}
