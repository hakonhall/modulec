package no.ion.modulec.compiler.single;

import no.ion.modulec.Context;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

/**
 * Implements Java compilation similar to <em>javac</em>.  In OpenJDK 17, this can be done in one of the following ways:
 *
 * <ol>
 *     <li>The <em>javac</em> program which has the {@link com.sun.tools.javac.Main} entrypoint (AFAIK),
 *     which invokes {@code com.sun.tools.javac.main.Main.compile(String[])}.</li>
 *     <li>Invoke {@link com.sun.tools.javac.Main#compile(String[], PrintWriter)}, which invokes {@code
 *     com.sun.tools.javac.main.Main.compile(String[])}.  This is the only concrete type not exposed behind
 *     an interface that is accessible for invoking the compiler by jdk.compiler, but is documented as "legacy",
 *     likely because it's not provided as a service.</li>
 *     <li>Get the "javac" {@link java.util.spi.ToolProvider ToolProvider} (in java.base) by invoking {@link
 *     java.util.spi.ToolProvider#findFirst(String)}, which returns {@code com.sun.tools.javac.main.JavacToolProvider},
 *     then invoke {@link java.util.spi.ToolProvider#run(PrintWriter, PrintWriter, String...)
 *     run(PrintWriter, PrintWriter, String...)} that
 *     invokes {@code com.sun.tools.javac.main.Main.compile(String[])}.</li>
 *     <li>Get a {@link javax.tools.Tool} instance from {@link javax.tools.ToolProvider#getSystemJavaCompiler()} in java.compiler,
 *     or by loading the service {@link javax.tools.JavaCompiler} or {@link javax.tools.Tool} (the former implements
 *     the latter).  This returns {@code com.sun.tools.javac.api.JavacTool}.  Then invoke {@link
 *     javax.tools.Tool#run(InputStream, OutputStream, OutputStream, String...)}, which invokes
 *     {@link com.sun.tools.javac.Main#compile(String[], PrintWriter)} (see above).</li>
 * </ol>
 *
 * <p>All of these defer to the unexported {@code com.sun.tools.javac.main.Main.compiler(String[])} in jdk.compiler in OpenJDK 17.
 * All arguments are passed as String arguments and as-if passed on the command-line to {@code javac}.
 * There is one other lower-level and equivalent way to compile Java code that (also) uses {@link javax.tools.JavaCompiler},
 * see {@link Compiler}.</p>
 */
class Javac {
    private final Context context;
    private final JavaCompiler javaCompiler;

    Javac(Context context) {
        this(context, Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "No system Java compiler available"));
    }

    Javac(Context context, JavaCompiler javaCompiler) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.javaCompiler = Objects.requireNonNull(javaCompiler, "javaCompiler cannot be null");
    }

    record Result(String message, boolean success) {}

    Result javac(List<String> arguments) {
        context.log().command("javac", arguments);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(128);
        String[] args = arguments.toArray(String[]::new);
        int exitCode = javaCompiler.run(null, byteArrayOutputStream, byteArrayOutputStream, args);
        String message = byteArrayOutputStream.toString();
        return new Result(message, exitCode == 0);
    }
}
