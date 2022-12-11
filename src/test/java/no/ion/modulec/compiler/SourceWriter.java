package no.ion.modulec.compiler;

import no.ion.modulec.file.Pathname;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceWriter {
    private static final Pattern PACKAGE_RE = Pattern.compile("^package ([a-zA-Z0-9.]+);", Pattern.MULTILINE);
    private static final Pattern CLASS_RE = Pattern.compile("^(public |protected|)class ([a-zA-Z0-9]+)", Pattern.MULTILINE);

    private final Pathname src;

    /** Returns a writer for source code rooted at src. */
    public static SourceWriter rootedAt(Pathname src) {
        return new SourceWriter(src);
    }

    public SourceWriter(Pathname src) {
        this.src = src;
    }

    public Pathname pathname() { return src; }
    public Path path() { return src.path(); }

    public SourceWriter writeModuleInfoJava(String content) {
        moduleInfoJavaPathname().makeParentDirectories().writeUtf8(content);
        return this;
    }

    public SourceWriter writeClass(String content) {
        Matcher packageMatcher = PACKAGE_RE.matcher(content);
        if (!packageMatcher.find())
            throw new IllegalArgumentException("Failed to find package: " + content);
        String packageName = packageMatcher.group(1);

        Matcher classMatcher = CLASS_RE.matcher(content);
        if (!classMatcher.find())
            throw new IllegalArgumentException("Failed to find class name: " + content);
        String simpleClassName = classMatcher.group(2);

        String path = packageName.replace('.', '/') + '/' + simpleClassName + ".java";
        src.resolve(path).makeParentDirectories().writeUtf8(content);
        return this;
    }

    private Pathname moduleInfoJavaPathname() { return src.resolve("module-info.java"); }
}
