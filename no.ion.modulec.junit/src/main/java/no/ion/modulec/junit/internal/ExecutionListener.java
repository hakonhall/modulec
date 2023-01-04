package no.ion.modulec.junit.internal;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Example from maven-surefire-plugin:
 *
 * <pre>
 * [INFO] --- maven-surefire-plugin:3.0.0-M6:test (default-test) @ no.ion.modulec ---
 * [INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
 * [INFO]
 * [INFO] -------------------------------------------------------
 * [INFO]  T E S T S
 * [INFO] -------------------------------------------------------
 * [INFO] Running no.ion.modulec.ModuleCompilerTest
 * [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.087 s - in no.ion.modulec.ModuleCompilerTest
 * [INFO] Running no.ion.modulec.BadModuleInfoTest
 * [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.155 s - in no.ion.modulec.BadModuleInfoTest
 * [INFO] Running no.ion.modulec.file.PathnameTest
 * [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.019 s - in no.ion.modulec.file.PathnameTest
 * [INFO] Running no.ion.modulec.BasicTest
 * [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.087 s - in no.ion.modulec.BasicTest
 * [INFO] Running no.ion.modulec.java.SourceWriterTest
 * [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s - in no.ion.modulec.java.SourceWriterTest
 * [INFO] Running no.ion.modulec.java.JavacTest
 * [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.239 s - in no.ion.modulec.java.JavacTest
 * [INFO] Running no.ion.modulec.java.MultiModuleCompilationAndPackagingTest
 * [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.036 s - in no.ion.modulec.java.MultiModuleCompilationAndPackagingTest
 * [INFO]
 * [INFO] Results:
 * [INFO]
 * [INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
 * [INFO]
 * [INFO]
 * </pre>
 */
public class ExecutionListener implements TestExecutionListener {
    private final Reporter reporter;
    private final Map<UniqueId, TestFileSummary> summaryById = new HashMap<>();
    private Map<UniqueId, Integer> testsByTestFile;
    private int width = 0;

    public ExecutionListener(Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {

    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        throw new UnsupportedOperationException("dynamic test not yet supported");
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        UniqueId parentId = testIdentifier.getParentIdObject().orElse(null);
        if (parentId != null) {
            TestFileSummary summary = summaryById.get(parentId);
            if (summary != null) {
                reporter.info(".");
                summary.addSkipped();
            }
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        UniqueId id = testIdentifier.getUniqueIdObject();
        Integer count = testsByTestFile.get(id);
        if (count != null) {
            if (summaryById.put(id, new TestFileSummary()) != null)
                throw new IllegalStateException("Test already started: " + id);
            //reporter.infof("Running %d tests in %s ...", count, qualifiedName(testIdentifier));
            reporter.infof("%s ", qualifiedName(testIdentifier));
            return;
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        UniqueId parentId = testIdentifier.getParentIdObject().orElse(null);
        if (parentId != null) {
            TestFileSummary summary = summaryById.get(parentId);
            if (summary != null) {
                TestExecutionResult.Status status = testExecutionResult.getStatus();
                switch (status) {
                    case ABORTED:
                        reporter.info(".");
                        summary.addAborted();
                        break;
                    case FAILED:
                        reporter.info(".");
                        summary.addFailed();
                        break;
                    case SUCCESSFUL:
                        reporter.info(".");
                        summary.addSuccess();
                        break;
                    default:
                        throw new IllegalStateException("Unknown status: " + status);
                }

                testExecutionResult.getThrowable()
                                   .ifPresent(throwable -> summary.addThrowable(testIdentifier, throwable));
                return;
            }
        }

        UniqueId id = testIdentifier.getUniqueIdObject();
        TestFileSummary summary = summaryById.get(id);
        if (summary != null) {
            var stat = new ArrayList<String>();
            if (summary.failed() > 0)
                stat.add(summary.failed() + " failed");
            if (summary.aborted() > 0)
                stat.add(summary.aborted() + " aborted");
            if (summary.skipped() > 0)
                stat.add(summary.skipped() + " failed");
            if (stat.isEmpty()) {
                reporter.infoln(" ok");
            } else {
                reporter.infofln(" " + String.join(", ", stat));
            }

            for (var failure : summary.failures()) {
                reporter.testFailure(qualifiedName(failure.testIdentifier()), failure.throwable());
            }

            // Fall-through: Verify no throwable is present
        }

        if (testExecutionResult.getThrowable().isPresent())
            throw new IllegalStateException("Throwable present for non-test: " + testIdentifier.getDisplayName() + ": " +
                                            testExecutionResult.getThrowable().get());
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        System.out.println("Reporting entry published: " + testIdentifier + ": " + entry.toString());
        TestExecutionListener.super.reportingEntryPublished(testIdentifier, entry);
    }

    public void setPlan(TestPlan plan) {
        Set<TestIdentifier> roots = plan.getRoots();
        if (roots.size() != 1) {
            throw new IllegalStateException("Found more than 1 root: " + roots);
        }
        TestIdentifier root = roots.iterator().next();
        if (!Objects.equals(root.getDisplayName(), "JUnit Jupiter"))
            throw new IllegalStateException("Expected JUnit Jupiter root test but found '" + root.getDisplayName() + "'");



        Map<UniqueId, Integer> testsByTestFile = new HashMap<>();
        this.width = populate(plan, root, testsByTestFile);
        this.testsByTestFile = Map.copyOf(testsByTestFile);
    }

    public boolean wasSuccess() {
        return summaryById.values().stream().allMatch(summary -> summary.success() == summary.count());
    }

    private static String qualifiedName(TestIdentifier testIdentifier) {
        final String displayName = testIdentifier.getDisplayName();

        Optional<TestSource> testSource = testIdentifier.getSource();
        if (testSource.isEmpty())
            return displayName;

        if (testSource.get() instanceof MethodSource) {
            MethodSource methodSource = (MethodSource) testSource.get();
            return methodSource.getClassName() + "." + displayName;
        }

        if (testSource.get() instanceof ClassSource) {
            ClassSource classSource = (ClassSource) testSource.get();
            return classSource.getClassName();
        }

        return displayName;
    }

    private static int populate(TestPlan plan, TestIdentifier root, Map<UniqueId, Integer> testFiles) {
        int width = 0;

        for (TestIdentifier testFile : plan.getChildren(root)) {
            if (testFile.getType() != TestDescriptor.Type.CONTAINER)
                throw new IllegalStateException("Child of root test is not a container of tests: " + testFile);
            width = Math.max(width, qualifiedName(testFile).length());

            Set<TestIdentifier> testMethods = plan.getChildren(testFile);
            for (var testMethod : testMethods) {
                if (testMethod.getType() != TestDescriptor.Type.TEST)
                    throw new IllegalStateException("Child of test file is not a test: " + testMethod);
            }

            testFiles.put(testFile.getUniqueIdObject(), testMethods.size());
        }

        return width;
    }
}
