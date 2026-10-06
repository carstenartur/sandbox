/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Installation discovery must distinguish a p2 home from PDE project templates. */
class InstalledHomeDiscoveryTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"eclipse", "eclipse.exe", "../MacOS/eclipse", "Eclipse.app/Contents/MacOS/eclipse"})
    void pdeTemplatePluginsDoNotCreateAnotherInstallation(String launcherLayout) throws Exception {
        Path home = install(temporary.resolve("installation"), launcherLayout);
        Path templates = home.resolve("plugins/org.eclipse.pde.build_1/templates/plugins");
        Files.createDirectories(templates);
        Files.writeString(templates.resolve("customBuildCallbacks.xml"), "<project/>");

        assertEquals(home, discover(home));
    }

    @Test
    void discoversTheMacApplicationBundleBelowTheInstallationDirectory() throws Exception {
        Path home = install(temporary.resolve("Eclipse.app/Contents/Eclipse"), "../MacOS/eclipse");
        assertEquals(home, discover(temporary));
    }

    @Test
    void twoRealInstallationsRemainAnError() throws Exception {
        install(temporary.resolve("first"), "eclipse");
        install(temporary.resolve("second"), "eclipse");
        assertThrows(IOException.class, () -> discover(temporary));
    }

    @Test
    void pluginsAndLauncherWithoutConfigurationAreNotAnInstallation() throws Exception {
        Path home = install(temporary.resolve("incomplete"), "eclipse");
        Files.delete(home.resolve("configuration/config.ini"));
        assertThrows(IOException.class, () -> discover(home));
    }

    @Test
    void pluginsAndConfigurationWithoutLauncherAreNotAnInstallation() throws Exception {
        Path home = install(temporary.resolve("incomplete"), "eclipse");
        Files.delete(home.resolve("eclipse"));
        assertThrows(IOException.class, () -> discover(home));
    }

    private static Path install(Path home, String layout) throws IOException {
        Files.createDirectories(home.resolve("plugins"));
        Files.createDirectories(home.resolve("configuration"));
        Files.writeString(home.resolve("configuration/config.ini"), "osgi.bundles=fixture\n");
        Path launcher = home.resolve(layout).normalize();
        Files.createDirectories(launcher.getParent());
        Files.writeString(launcher, "native launcher fixture\n");
        return home;
    }

    private static Path discover(Path installation) throws Exception {
        var method = InstalledMathematicsVerifier.class.getDeclaredMethod("installedHome", Path.class);
        method.setAccessible(true);
        try {
            return (Path) method.invoke(null, installation);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }
}
