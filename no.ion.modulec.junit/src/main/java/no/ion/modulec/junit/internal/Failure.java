package no.ion.modulec.junit.internal;

import org.junit.platform.launcher.TestIdentifier;

class Failure {
    private final TestIdentifier testIdentifier;
    private final Throwable throwable;

    Failure(TestIdentifier testIdentifier, Throwable throwable) {
        this.testIdentifier = testIdentifier;
        this.throwable = throwable;
    }

    TestIdentifier testIdentifier() { return testIdentifier; }
    Throwable throwable() { return throwable; }
}
