package no.ion.modulec.junit;

import no.ion.modulec.junit.internal.ExecutionListener;
import no.ion.modulec.junit.internal.Reporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

public class Main {
    private final Reporter reporter = new Reporter();
    private final ExecutionListener executionListener = new ExecutionListener(reporter);
    private final Path testDir;

    public static void main(String[] args) {
        if (args.length < 1)
            fail("missing TESTJAR argument: Either test JAR or exploded test JAR directory");
        var testPath = Path.of(args[0]);

        System.exit(runTests(testPath));
    }

    public static int runTests(Path testPath) {
        return new Main(testPath).run();
    }

    private Main(Path testDir) {
        this.testDir = testDir;
    }

    public int run() {
        if (Files.isDirectory(testDir)) {
            // OK: exploded JAR
        } else if (Files.isRegularFile(testDir) && testDir.getFileName().toString().endsWith(".jar")) {
            // OK: JAR
        } else {
            reporter.infoln("error: no such JAR file or exploded JAR directory: " + testDir);
            return 1;
        }

        var request = LauncherDiscoveryRequestBuilder.request()
                                                     // The module this code is running a part of, must be able to "read"
                                                     // the module containing the classes corresponding to the *.class
                                                     // files in this directory.
                                                     .selectors(selectClasspathRoots(Set.of(testDir)))
                                                     // true, triggers loading of non-existent junit-platform.properties
                                                     .enableImplicitConfigurationParameters(false)
                                                     .filters(includeClassNamePatterns(".*Test"))
                                                     .build();

        LauncherConfig launcherConfig = LauncherConfig.builder()
                                                      .enableLauncherSessionListenerAutoRegistration(false)
                                                      .enableLauncherDiscoveryListenerAutoRegistration(false)
                                                      .enableTestExecutionListenerAutoRegistration(false)
                                                      .enableTestEngineAutoRegistration(false)
                                                      .enablePostDiscoveryFilterAutoRegistration(false)
                                                      // org.junit.jupiter.engine.JupiterTestEngine is NOT exported from junit-jupiter-engine,
                                                      // therefore its package needs to be made exported in the modular JAR's module descriptor.
                                                      .addTestEngines(new JupiterTestEngine())
                                                      .addTestExecutionListeners(executionListener)
                                                      .build();

        try (LauncherSession session = LauncherFactory.openSession(launcherConfig)) {
            Launcher launcher = session.getLauncher();

            // This JHMS application must be launched with the --context-class-loader option corresponding to the
            // module being tested.  We can therefore just pass that through to JUnit 5, which uses the context
            // class loader to locate and load test classes.
            //
            // E.g. the org.junit.jupiter.api.Test annotations on methods in the hybrid module being tested (whose class
            // loader is specified by --context-class-loader), will be compared to Test annotation resolved by this
            // no.ion.modulec.junit hybrid module.
            //  - This module (no.ion.module.junit) resolves e.g. Test from the transitive hybrid module dependency
            //    org.junit.jupiter.api@5.9.1.
            //  - Therefore, the hybrid module being tested (--context-class-loader) must also depend on
            //    org.junit.jupiter.api@5.9.1.
            // This is not obvious, so sanity-check this here.
            if (!sanityCheckDependencies()) return 1;

            TestPlan plan = launcher.discover(request);
            if (!plan.containsTests()) {
                reporter.infoln("No tests found");
                return 0;
            }

            executionListener.setPlan(plan);

            launcher.execute(plan);
        }

        return executionListener.wasSuccess() ? 0 : 1;
    }

    private boolean sanityCheckDependencies() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String qualifiedTestClassName = Test.class.getName();
        final Class<?> testClass;
        try {
            testClass = classLoader.loadClass(qualifiedTestClassName);
        } catch (ClassNotFoundException e) {
            reporter.infoln("error: the Hybrid Module " + classLoader.getName() + " must depend on " + Test.class.getClassLoader().getName());
            return false;
        }
        if (testClass != Test.class) {
            // A Hybrid Module's toString() returns the ID of the hybrid module, i.e. of the form NAME@VERSION.
            reporter.infoln("error: the Hybrid Module " + classLoader.getName() + " was found to depend on " +
                            testClass.getClassLoader().getName() + " but must depend on " + Test.class.getClassLoader().getName());
            return false;
        }
        return true;
    }

    private static void fail(String message) {
        System.err.println("error: " + message);
        System.exit(1);
    }
}
