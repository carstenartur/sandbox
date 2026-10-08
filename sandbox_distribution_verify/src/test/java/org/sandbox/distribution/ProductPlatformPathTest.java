/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Materialized product homes include a native application bundle on macOS. */
class ProductPlatformPathTest {
    @ParameterizedTest
    @CsvSource({
            "Linux, amd64, products/sandbox/linux/gtk/x86_64",
            "Windows 11, amd64, products/sandbox/win32/win32/x86_64",
            "Mac OS X, x86_64, products/sandbox/macosx/cocoa/x86_64",
            "Mac OS X, x86_64, products/sandbox/macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse",
            "Mac OS X, aarch64, products/sandbox/macosx/cocoa/aarch64/Eclipse.app/Contents/Eclipse"
    })
    void recognizesTheNativeProductHome(String os, String arch, String home) throws Exception {
        assertTrue(DistributionVerifier.Platform.from(os, arch).matchesProductPath(Path.of(home)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "products/sandbox/linux/gtk/x86_64",
            "products/sandbox/win32/win32/x86_64",
            "products/sandbox/macosx/cocoa/aarch64/Eclipse.app/Contents/Eclipse",
            "products/sandbox/macosx/gtk/x86_64/Eclipse.app/Contents/Eclipse",
            "products/macosx/other/cocoa/x86_64",
            "products/sandbox/macosx/cocoa/x86_64/other/Contents/Eclipse",
            "products/sandbox/macosx/cocoa/x86_64/Eclipse.app/Contents/other",
            "products/sandbox/macosx/cocoa/x86_64-extra/Eclipse.app/Contents/Eclipse"
    })
    void rejectsOtherPlatformsAndUnrelatedNestedDirectories(String home) throws Exception {
        assertFalse(DistributionVerifier.Platform.from("Mac OS X", "x86_64")
                .matchesProductPath(Path.of(home)));
    }
}
