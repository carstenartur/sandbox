/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MacInstallationLayoutTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @CsvSource({
            "Mac OS X, fresh-install/Eclipse.app, fresh-install/Eclipse.app/Contents/Eclipse",
            "Linux, fresh-install, fresh-install",
            "Windows 11, fresh-install, fresh-install"
    })
    void directorDestinationAndBundlePoolUseTheNativeLayout(String os, String destination, String home)
            throws Exception {
        var paths = DistributionVerifier.Platform.from(os, "x86_64")
                .installationPaths(Path.of("fresh-install"));
        assertEquals(Path.of(destination), paths.destination());
        assertEquals(Path.of(home), paths.home());
    }

    @Test
    void aggregateBuilderFindsTheFreshNativeApplicationHome() throws Exception {
        Path home = Files.createDirectories(temporary.resolve("Eclipse.app/Contents/Eclipse"));
        assertEquals(home, aggregateHome(temporary));
    }

    @Test
    void aggregateStagesStillFindTheDirectApplicationHome() throws Exception {
        Path application = temporary.resolve("stage.app");
        Path home = Files.createDirectories(application.resolve("Contents/Eclipse"));
        assertEquals(home, aggregateHome(application));
    }

    @Test
    void aggregateStagesKeepTheFlatInstallationHome() throws Exception {
        assertEquals(temporary, aggregateHome(temporary));
    }

    private static Path aggregateHome(Path installation) throws Exception {
        var method = AggregateInstallationVerifier.class.getDeclaredMethod("home", Path.class);
        method.setAccessible(true);
        return (Path) method.invoke(null, installation);
    }
}
