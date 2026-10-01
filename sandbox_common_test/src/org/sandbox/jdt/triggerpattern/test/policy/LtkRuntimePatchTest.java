/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the fail-closed boundary before an LTK replacement may be published. */
class LtkRuntimePatchTest {
    @TempDir
    Path directory;

    @Test
    void acceptsNewQualifierWithIdenticalRuntimeContract() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path patch = bundle("patch.jar", "3.16.0.v20260912-1900", null, null);
        assertEquals("3.16.0.v20260912-1900", LtkRuntimePatch.validateBundle(patch, stock));
    }

    @Test
    void rejectsAChangedDependencyInsteadOfCallingItCompatible() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path patch = bundle("patch.jar", "3.16.0.v20260912-1900", "Require-Bundle", "new.dependency");
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(patch, stock));
    }

    @Test
    void rejectsMissingExports() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path patch = bundle("patch.jar", "3.16.0.v20260912-1900", "Export-Package", "other.package");
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(patch, stock));
    }

    @Test
    void rejectsAnUnchangedOrOlderVersion() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path unchanged = bundle("same.jar", "3.16.0.v20260702-0744", null, null);
        Path older = bundle("older.jar", "3.16.0.v20260101-0000", null, null);
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(unchanged, stock));
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(older, stock));
    }

    @Test
    void rejectsADifferentReleaseEvenWhenNumericallyNewer() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path patch = bundle("patch.jar", "3.17.0.v20260912-1900", null, null);
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(patch, stock));
    }

    @Test
    void rejectsAnUnqualifiedVersion() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path patch = bundle("patch.jar", "3.16.0.qualifier", null, null);
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(patch, stock));
    }

    @Test
    void rejectsAnUnexpectedStockTarget() throws Exception {
        Path stock = bundle("stock.jar", "3.15.0.v20260601-0000", null, null);
        Path patch = bundle("patch.jar", "3.16.0.v20260912-1900", null, null);
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(patch, stock));
    }

    @Test
    void rejectsADifferentBundleIdentity() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path patch = bundle("patch.jar", "3.16.0.v20260912-1900", "Bundle-SymbolicName", "other.bundle;singleton:=true");
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(patch, stock));
    }

    @Test
    void rejectsMissingFileCountImplementation() throws Exception {
        Path stock = bundle("stock.jar", "3.16.0.v20260702-0744", null, null);
        Path patch = directory.resolve("empty.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(patch), manifest("3.16.0.v20260912-1900"))) {
            out.putNextEntry(new JarEntry("unrelated.txt"));
            out.write(1);
        }
        assertThrows(IOException.class, () -> LtkRuntimePatch.validateBundle(patch, stock));
    }

    private Path bundle(String name, String version, String header, String value) throws Exception {
        Manifest manifest = manifest(version);
        if (header != null) {
            manifest.getMainAttributes().putValue(header, value);
        }
        Path jar = directory.resolve(name);
        try (var out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("org/eclipse/ltk/core/refactoring/CompositeChange.class"));
            out.write(new byte[] {1, 2, 3});
            out.closeEntry();
        }
        return jar;
    }

    private static Manifest manifest(String version) {
        Manifest manifest = new Manifest();
        Attributes headers = manifest.getMainAttributes();
        headers.putValue("Manifest-Version", "1.0");
        headers.putValue("Bundle-SymbolicName", "org.eclipse.ltk.core.refactoring; singleton:=true");
        headers.putValue("Bundle-Version", version);
        headers.putValue("Require-Bundle", "org.eclipse.core.runtime;bundle-version=\"[3.29.0,4.0.0)\"");
        headers.putValue("Export-Package", "org.eclipse.ltk.core.refactoring");
        headers.putValue("Bundle-RequiredExecutionEnvironment", "JavaSE-17");
        return manifest;
    }
}
