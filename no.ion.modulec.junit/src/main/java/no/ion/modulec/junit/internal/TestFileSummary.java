package no.ion.modulec.junit.internal;

import org.junit.platform.launcher.TestIdentifier;

import java.util.ArrayList;
import java.util.List;

class TestFileSummary {
    private final List<Failure> failures = new ArrayList<>();

    private int skipped = 0;
    private int success = 0;
    private int failed = 0;
    private int aborted = 0;

    TestFileSummary() {}
    void addSkipped() { skipped++; }
    void addSuccess() { success++; }
    void addFailed() { failed++; }
    void addAborted() { aborted++; }

    void addThrowable(TestIdentifier testIdentifier, Throwable throwable) {
        failures.add(new Failure(testIdentifier, throwable));
    }

    int count() { return skipped + success + failed + aborted; }
    int skipped() { return skipped; }
    int success() { return success; }
    int failed() { return failed; }
    int aborted() { return aborted; }
    List<Failure> failures() { return List.copyOf(failures); }
}
