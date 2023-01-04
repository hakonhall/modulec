package no.ion.modulec.file;

import java.util.function.Consumer;

public class TestDirectory {
    private static TemporaryDirectory create(Class<?> testClass) {
        return Pathname.makeTmpdir(testClass.getName() + ".", "", FileMode.fromModeInt(0700));
    }

    public static void with(Class<?> testClass, Consumer<Pathname> callback) {
        try (TemporaryDirectory temporaryDirectory = create(testClass)) {
            callback.accept(temporaryDirectory.directory());
        }
    }
}
