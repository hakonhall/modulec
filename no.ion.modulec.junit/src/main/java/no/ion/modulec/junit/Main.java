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

        // HOW THIS WORKS
        //
        // junit will walk the testDir file tree (or search the testDir JAR) for *.class files.
        // includeClassNamePatterns narrows the fileset further.  All resulting qualified class names
        // are then loaded with the current thread's context class loader.
        //
        // The current thread's context class loader must be set by the caller of runTests()
        // (done in no.ion.modulec), or with the -c/--context-class-loader option to javahms if this
        // class is the main class of a javahms invocation.  Either way, the class loader MUST be
        // the class loader of the hybrid module being tested.
        //
        // The Class<?> objects are then inspected to find e.g. annotations and comparing them with
        // the org.junit.jupiter.api.Test.class literal to find which tests to run via reflection.
        //
        // Let's slow down and repeat:
        //   1. the hybrid module being tested reads a hybrid module M@V that contains such junit annotations,
        //      e.g. a hybrid module M made from the org.junit.jupiter:junit-jupiter-api JAR at version V=5.9.1.
        //   2. the junit of this (no.ion.modulec.junit) module reads a hybrid module N@U that contains
        //      such junit annotations.
        // Therefore, M@V MUST BE IDENTICAL TO N@U.  Only if this is the case, will they be represented by
        // the exact same hybrid module, and therefore resolve e.g. "org.junit.jupiter.api.Test" to the
        // identically same classes. This is not obvious, so run a sanity-check.
        //
        // It also means that all hybrid modules included in the module graph rooted at this hybrid module
        // no.ion.modulec.junit should be
        if (!sanityCheckDependencies()) return 1;

        var request = LauncherDiscoveryRequestBuilder.request()
                                                     .selectors(selectClasspathRoots(Set.of(testDir)))
                                                     // disable loading of non-existent junit-platform.properties
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
