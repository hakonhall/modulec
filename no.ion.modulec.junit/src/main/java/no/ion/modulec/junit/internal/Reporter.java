package no.ion.modulec.junit.internal;

import java.util.Objects;

public class Reporter {
    public Reporter() {}

    public void info(String message) {
        System.out.print(message);
        System.out.flush();
    }

    public void infoln(String message) {
        System.out.println(message);
        System.out.flush();
    }

    public void infof(String format, Object... args) {
        System.out.printf(format, args);
        System.out.flush();
    }

    public void infofln(String format, Object... args) {
        infof(format + '\n', args);
    }

    void testFailure(String name, Throwable throwable) {
        System.out.print("Test " + name + " failed: ");

        var string = new StringBuilder();
        appendStackTrace(string, throwable);

        System.out.print(string);
        System.out.flush();
    }

    private static void appendStackTrace(StringBuilder string, Throwable throwable) {
        appendStackTraceLow(string, throwable);
        for (var suppressed : throwable.getSuppressed()) {
            string.append("Suppressed: ");
            appendStackTraceLow(string, suppressed);
        }

        Throwable cause = throwable.getCause();
        if (cause != null) {
            string.append("Caused by: ");
            appendStackTrace(string, cause);
        }
    }

    private static void appendStackTraceLow(StringBuilder string, Throwable throwable) {
        string.append(throwable.toString()).append('\n');
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int maxIndex = maxIndex(stackTrace);
        for (int i = 0; i < maxIndex; ++i) {
            string.append("\tat ");
            StackTraceElement element = stackTrace[i];
            String moduleName = element.getModuleName();
            if (moduleName == null || moduleName.isEmpty()) {
                string.append(element.getClassLoaderName()).append("//");
            } else {
                string.append(moduleName).append('/');
            }
            string.append(element.getClassName()).append('.').append(element.getMethodName()).append('(');

            String filename = element.getFileName();
            if (filename == null) {
                string.append("<unknown>");
            } else {
                string.append(filename).append(':').append(element.getLineNumber());
            }
            string.append(")\n");
        }
    }

    private static int maxIndex(StackTraceElement[] stackTrace) {
        int index = 0;

        do {
            if (index == stackTrace.length)
                return index;

            String classLoaderName = stackTrace[index].getClassLoaderName();
            if ((classLoaderName != null && classLoaderName.startsWith("org.junit.platform.commons@")) &&
                Objects.equals(stackTrace[index].getClassName(), "org.junit.platform.commons.util.ReflectionUtils") &&
                Objects.equals(stackTrace[index].getMethodName(), "invokeMethod")) {
                break;
            }

            ++index;
        } while (true);

        do {
            if (index == 0)
                return stackTrace.length;

            --index;

            if (Objects.equals(stackTrace[index].getModuleName(), "java.base") &&
                Objects.equals(stackTrace[index].getClassName(), "jdk.internal.reflect.NativeMethodAccessorImpl") &&
                Objects.equals(stackTrace[index].getMethodName(), "invoke0")) {
                return index;
            }
        } while (true);
    }
}
