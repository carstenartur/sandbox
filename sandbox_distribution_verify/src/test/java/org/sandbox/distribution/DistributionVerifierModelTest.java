/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the real entry point, stopping before any product/network work. */
class DistributionVerifierModelTest {
    private static final List<String> IDS = List.of("bcutil", "bcprov", "bcpkix", "bcpg");
    private static final String RELEASE = "https://download.eclipse.org/releases/2026-09/";
    private static final String ORBIT = "https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2026-09/";

    @TempDir
    Path root;

    @Test
    void validDistinctDeclarationsReachArtifactVerification() throws Exception {
        fixture();
        // No built repository: reaching this diagnostic proves the complete model passed.
        assertFailure("Built p2 repository does not exist: " + root.resolve("sandbox_updatesite/target/repository"));
    }

    @ParameterizedTest(name = "{0}: {1}, version {2}, prepend={3}")
    @MethodSource("duplicateDeclarations")
    void rejectsDuplicatesBeforeTheyCanOverwriteAnEarlierValue(
            boolean maven, String id, String version, boolean prepend) throws Exception {
        fixture();
        Path file = root.resolve(maven ? "pom.xml" : "sandbox_target/eclipse.target");
        String closing = maven ? "</extraRequirements>" : "</location>";
        String opening = maven ? "<extraRequirements>" : "<location>";
        String duplicate = maven ? requirement(id, version) : unit(id, version);
        String original = Files.readString(file, StandardCharsets.UTF_8);
        String changed = prepend ? original.replace(opening, opening + duplicate)
                : original.replace(closing, duplicate + closing);
        Files.writeString(file, changed, StandardCharsets.UTF_8);

        assertFailure((maven ? "Duplicate Bouncy Castle Maven extra requirement: "
                : "Duplicate Bouncy Castle target unit: ") + id);
        assertEquals(changed, Files.readString(file, StandardCharsets.UTF_8), "Validation must not rewrite its input");
    }

    static Stream<Arguments> duplicateDeclarations() {
        return Stream.of(false, true).flatMap(maven -> IDS.stream().flatMap(id ->
                Stream.of(version(id), "9.9.9").flatMap(version -> Stream.of(false, true)
                        .map(prepend -> Arguments.of(maven, id, version, prepend)))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bcutil", "bcprov", "bcpkix", "bcpg"})
    void missingTargetDeclarationsStillFail(String id) throws Exception {
        fixture();
        Path target = root.resolve("sandbox_target/eclipse.target");
        String original = Files.readString(target, StandardCharsets.UTF_8);
        Files.writeString(target, original.replace(unit(id, version(id)), ""), StandardCharsets.UTF_8);
        Exception failure = assertThrows(Exception.class, () -> DistributionVerifier.main(new String[] { root.toString() }));
        assertEquals("Target Bouncy Castle units are incomplete", failure.getMessage().split(":", 2)[0]);
    }

    private void assertFailure(String message) {
        Exception failure = assertThrows(Exception.class, () -> DistributionVerifier.main(new String[] { root.toString() }));
        assertEquals(message, failure.getMessage());
        assertFalse(Files.exists(root.resolve("target/distribution-verification/verification.json")),
                "A rejected distribution must not emit PASS evidence");
    }

    private void fixture() throws IOException {
        String requirements = IDS.stream().map(id -> requirement(id, version(id))).reduce("", String::concat);
        String units = IDS.stream().map(id -> unit(id, version(id))).reduce("", String::concat);
        write("pom.xml", "<project><repositories>" + repository(RELEASE) + repository(ORBIT)
                + "</repositories><properties><bouncycastle.version>1.85</bouncycastle.version>"
                + "<bouncycastle.bcprov.version>1.85.2</bouncycastle.bcprov.version></properties>"
                + "<extraRequirements>" + requirements + "</extraRequirements><profiles><profile>"
                + "<id>distribution</id><modules><module>sandbox_product</module>"
                + "<module>sandbox_updatesite</module><module>sandbox_distribution_verify</module>"
                + "</modules></profile></profiles></project>");
        write("sandbox_target/eclipse.target", "<target><locations><location><repository location=\"" + RELEASE
                + "\"/><repository location=\"" + ORBIT + "\"/>" + units + "</location></locations></target>");
        String features = Stream.of("org.eclipse.platform", "org.eclipse.jdt", "org.eclipse.pde",
                "org.eclipse.equinox.p2.user.ui", "org.eclipse.egit", "org.eclipse.jgit", "sandbox_sample_feature")
                .map(id -> "<feature id=\"" + id + "\"/>").reduce("", String::concat);
        write("sandbox_product/sandbox.product", "<product application=\"org.eclipse.ui.ide.workbench\""
                + " includeLaunchers=\"true\"><repositories><repository location=\"" + RELEASE
                + "\"/></repositories><features>" + features + "</features></product>");
        write("sandbox_updatesite/category.xml", "<site><feature id=\"sandbox_sample_feature\"/></site>");
        String deliveryPom = "<project><dependencies><dependency><artifactId>sandbox_sample_feature</artifactId>"
                + "</dependency></dependencies></project>";
        write("sandbox_product/pom.xml", deliveryPom);
        write("sandbox_updatesite/pom.xml", deliveryPom);
        write("sandbox_oomph/sandbox.setup", "<setupTask name=\"eclipse.target.version\" defaultValue=\"2026-09\"/>");
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String repository(String url) {
        return "<repository><layout>p2</layout><url>" + url + "</url></repository>";
    }

    private static String version(String id) {
        return "bcprov".equals(id) ? "1.85.2" : "1.85.0";
    }

    private static String unit(String id, String version) {
        return "<unit id=\"" + id + "\" version=\"" + version + "\"/>";
    }

    private static String requirement(String id, String version) {
        return "<requirement><id>" + id + "</id><versionRange>" + version + "</versionRange></requirement>";
    }
}
