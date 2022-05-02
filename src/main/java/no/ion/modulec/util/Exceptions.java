package no.ion.modulec.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;

public class Exceptions {
    @FunctionalInterface public interface ThrowingRunnable<E extends Exception> { void run() throws E; }
    @FunctionalInterface public interface ThrowingSupplier<T, E extends Exception> { T get() throws E; }

    @SafeVarargs
    public static void uncheckIO(ThrowingRunnable<IOException> runnable, Class<? extends IOException>... ignored) {
        uncheckIO(() -> { runnable.run(); return null; }, ignored);
    }

    @SafeVarargs
    public static <T> T uncheckIO(ThrowingSupplier<T, IOException> supplier, Class<? extends IOException>... ignored) {
        try {
            return supplier.get();
        } catch (IOException e) {
            for (Class<? extends IOException> ignoredClass : ignored) {
                if (ignoredClass.isInstance(e)) {
                    return null;
                }
            }

            throw new UncheckedIOException(e);
        }
    }

    /** Wraps an InterruptedException in a RuntimeException. */
    public static <T> T uncheckInterrupted(ThrowingSupplier<T, InterruptedException> supplier) {
        try {
            return supplier.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Wraps an InterruptedException in a RuntimeException. */
    public static void uncheckInterrupted(ThrowingRunnable<InterruptedException> runnable) {
        try {
            runnable.run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void uncheckURISyntax(ThrowingRunnable<URISyntaxException> runnable) {
        uncheckURISyntax(() -> { runnable.run(); return null; });
    }

    /** Wraps a URISyntaxException in IllegalArgumentException, like {@link java.net.URI#create(String) URI.create(String)} does. */
    public static <T> T uncheckURISyntax(ThrowingSupplier<T, URISyntaxException> supplier) {
        // IllegalArgumentException is used in URI.create()
        try { return supplier.get(); } catch (URISyntaxException e) { throw new IllegalArgumentException(e.getMessage(), e); }
    }
}
