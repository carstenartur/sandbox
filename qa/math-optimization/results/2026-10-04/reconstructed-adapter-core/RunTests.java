import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import java.io.PrintWriter;
public class RunTests {
    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request();
        for (String name : args) {
            request.selectors(name.contains("#") ? DiscoverySelectors.selectMethod(name) : DiscoverySelectors.selectClass(name));
        }
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request.build(), listener);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out));
        if (summary.getTestsFoundCount() == 0 || summary.getTestsSkippedCount() != 0 || summary.getTotalFailureCount() != 0) System.exit(1);
    }
}
