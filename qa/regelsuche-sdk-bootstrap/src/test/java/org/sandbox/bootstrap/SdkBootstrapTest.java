/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SdkBootstrapTest {
    @TempDir Path temp;

    @Test
    void freshCustomRepositoryIsPopulatedOnceAndConflictingBytesAreRejected() throws Exception {
        Path repo = temp.resolve("different Maven repository");
        Path build = Files.createDirectory(temp.resolve("verified build"));
        var contents = new LinkedHashMap<String, String>();
        for (String name : new String[] {"parent.pom", "sdk.pom", "sdk.jar"}) {
            Path file = Files.writeString(build.resolve(name), name);
            contents.put(name, SdkBootstrap.sha256(file));
        }
        AtomicInteger builds = new AtomicInteger();
        SdkBootstrap.Acquisition acquire = () -> {
            builds.incrementAndGet();
            Files.createDirectories(repo);
            for (String name : contents.keySet()) Files.copy(build.resolve(name), repo.resolve(name));
        };
        SdkBootstrap.ensure(repo, contents, acquire);
        SdkBootstrap.ensure(repo, contents, acquire);
        assertEquals(1, builds.get());
        Files.writeString(repo.resolve("sdk.pom"), "different dependency model");
        assertThrows(IOException.class, () -> SdkBootstrap.ensure(repo, contents, acquire));
        Files.delete(repo.resolve("parent.pom"));
        assertThrows(IOException.class, () -> SdkBootstrap.ensure(repo, contents, acquire));
        assertEquals(1, builds.get());
        Files.delete(repo.resolve("sdk.pom"));
        assertThrows(IOException.class, () -> SdkBootstrap.ensure(repo, contents, () -> {}));
    }

    @Test
    void checkoutChecksTheExactTreeBeforeUsingSource() throws Exception {
        Path remote = temp.resolve("upstream");
        try (Git fixture = Git.init().setDirectory(remote.toFile()).call()) {
            Files.writeString(remote.resolve("pom.xml"), "<project/>\n");
            fixture.add().addFilepattern("pom.xml").call();
            var pin = fixture.commit().setMessage("first").setAuthor("test", "test@example.org")
                    .setCommitter("test", "test@example.org").call();
            Files.writeString(remote.resolve("pom.xml"), "changed\n");
            fixture.add().addFilepattern("pom.xml").call();
            fixture.commit().setMessage("second").setAuthor("test", "test@example.org")
                    .setCommitter("test", "test@example.org").call();
            Path checked = temp.resolve("checked");
            SdkBootstrap.checkout(checked, remote.toUri().toString(), pin.name(), pin.getTree().name());
            assertEquals("<project/>\n", Files.readString(checked.resolve("pom.xml")));
            assertThrows(IOException.class, () -> SdkBootstrap.checkout(temp.resolve("wrong"),
                    remote.toUri().toString(), pin.name(), "0".repeat(40)));
            Path reverseIndex = checked.resolve(".git/objects/pack/fixture.rev");
            Files.writeString(reverseIndex, "read-only pack index");
            assertTrue(reverseIndex.toFile().setReadOnly());
            SdkBootstrap.removeCheckout(checked);
            assertTrue(Files.notExists(checked));
        }
    }

    @Test
    void qualificationPinsCompleteDescriptorAndRuntimeClosure() throws Exception {
        var receipt = SdkBootstrap.Receipt.read(Path.of("../../sandbox_math_cleanup/lib/qualification.json"));
        assertEquals(13, receipt.files().size());
        assertTrue(receipt.files().containsValue(receipt.sdkHash()));
        assertThrows(IOException.class, () -> SdkBootstrap.ensure(temp, Map.of("absent", "0".repeat(64)), () -> {}));
    }
}
