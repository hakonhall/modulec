package no.ion.modulec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface MessageSink {

    /**
     * Log output from javac, jar, java, JUnit tests progress, etc.  Does NOT append a newline.  Example:
     *
     * """
     * src/test/java/no/FooTest.java:11: warning: [type] static method should be qualified by type name, no.Foo, instead of by an expression
     *         assertEquals(42, foo.answer());
     *                             ^
     * error: warnings found and -Werror specified
     * 1 error
     * 1 warning
     * """
     *  */
    default void info(String message) { log(Type.INFO, message); }
    default void infoLine(String message) { info(message + '\n'); }
    default void infoFormat(String format, Object... args) { info(format.formatted(args)); }

    /** A milestone has been reached, e.g. "compiled 2 source files in src/main/java to out/classes in 0.1s". */
    default void milestone(String achievement) { log(Type.MILESTONE, achievement + '\n'); }
    default void milestone(String format, Object... args) { milestone(format.formatted(args)); }

    /** A significant command is about to be invoked, e.g. ["javac", "-d", "classes", "src/Foo.java"]. */
    default void command(String... command) { command(List.of(command)); }
    default void command(String program, List<String> arguments) {
        List<String> union = new ArrayList<>();
        union.add(program);
        union.addAll(arguments);
        command(union);
    }
    default void command(List<String> command) {
        log(Type.COMMAND, () -> command.stream().map(MessageSink::shellEscapeArgument).collect(Collectors.joining(" ", "", "\n")));
    }

    default void debug(Supplier<String> message) { log(Type.DEBUG, message); }
    default void debugLine(Supplier<String> message) { debug(() -> message.get() + '\n'); }
    default void debugFormat(String format, Object... args) { debug(() -> format.formatted(args)); }

    enum Type { COMMAND, DEBUG, INFO, MILESTONE }
    void log(Type type, Supplier<String> message);
    default void log(Type type, String message) { log(type, () -> message); }
    default void logLine(Type type, Supplier<String> message) { log(type, () -> message.get() + '\n'); }
    default void logLine(Type type, String message) { log(type, message + '\n'); }
    default void logFormat(Type type, String format, Object... args) { log(type, () -> format.formatted(args)); }

    private static String shellEscapeArgument(String argument) {
        if (!needQuoting(argument)) return argument;

        StringBuilder escaped = new StringBuilder().append('"');

        argument.codePoints()
                .forEach(cp -> {
                    switch (cp) {
                        case '$':
                        case '`':
                        case '"':
                        case '\\':
                        case '\n':
                            escaped.append("\\").append(Character.toString(cp));
                            break;
                        default:
                            escaped.append(Character.toString(cp));
                    }
                });

        escaped.append('"');
        return escaped.toString();
    }

    private static boolean needQuoting(String argument) {
        return argument.codePoints()
                       .anyMatch(cp -> {
                           if (Character.isLetterOrDigit(cp)) return false;
                           if ("_-@%/=+^.:".indexOf(cp) != -1) return false;
                           return true;
                       });
    }
}
