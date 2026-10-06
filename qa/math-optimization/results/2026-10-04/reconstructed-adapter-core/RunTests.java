import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
public class RunTests {
    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request();
        for (String name : args) {
            request.selectors(name.contains("#") ? DiscoverySelectors.selectMethod(name) : DiscoverySelectors.selectClass(name));
        }
        var listener = new SummaryGeneratingListener();
        var incomplete = new AtomicBoolean();
        var statusListener = new TestExecutionListener() {
            @Override public void executionSkipped(org.junit.platform.launcher.TestIdentifier identifier, String reason) {
                incomplete.set(true);
            }
            @Override public void executionFinished(org.junit.platform.launcher.TestIdentifier identifier, TestExecutionResult result) {
                if (result.getStatus() == TestExecutionResult.Status.ABORTED) incomplete.set(true);
            }
        };
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener, statusListener);
        launcher.execute(request.build());
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out));
        if (summary.getTestsFoundCount() == 0 || summary.getTestsSkippedCount() != 0 || summary.getTotalFailureCount() != 0 || incomplete.get()) System.exit(1);
    }
}
