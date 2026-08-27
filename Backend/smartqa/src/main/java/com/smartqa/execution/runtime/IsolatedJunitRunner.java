package com.smartqa.execution.runtime;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public final class IsolatedJunitRunner {

    private IsolatedJunitRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Missing test class name");
            System.exit(2);
        }
        Class<?> testClass = Class.forName(args[0]);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(testClass))
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        System.out.println("SMARTQA_SUMMARY succeeded=" + summary.getTestsSucceededCount()
                + " failed=" + summary.getTotalFailureCount()
                + " started=" + summary.getTestsStartedCount());
        if (summary.getTotalFailureCount() > 0) {
            summary.getFailures().forEach(failure -> {
                System.err.println(failure.getTestIdentifier().getDisplayName());
                if (failure.getException() != null) {
                    failure.getException().printStackTrace(System.err);
                }
            });
            System.exit(1);
        }
        System.exit(0);
    }
}
