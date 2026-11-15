import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

public final class PublicInstallerAgent {
    public static void premain(String argument, Instrumentation instrumentation) {
        Thread driver = new Thread(() -> {
            try {
                String[] options = argument.split(";", 3);
                Class<?> display = null;
                for (int i = 0; i < 900 && display == null; ++i) {
                    display = Arrays.stream(instrumentation.getAllLoadedClasses())
                            .filter(type -> type.getName().equals("org.eclipse.swt.widgets.Display"))
                            .findFirst().orElse(null);
                    if (display == null) Thread.sleep(200);
                }
                if (display == null) throw new IllegalStateException("SWT Display was never loaded");
                URL[] location = { Path.of(options[0]).toUri().toURL() };
                // Load the UI driver with the installer's own SWT types and native libraries.
                // No replacement application, workspace, Oomph model or bundle is installed.
                try (URLClassLoader loader = new URLClassLoader(location, display.getClassLoader())) {
                    Class<?> ui = Class.forName("PublicInstallerUi", true, loader);
                    ui.getMethod("drive", Path.class, String.class)
                            .invoke(null, Path.of(options[1]), options[2]);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                Runtime.getRuntime().halt(71);
            }
        }, "public-installer-acceptance");
        driver.setDaemon(true);
        driver.start();
    }
}
