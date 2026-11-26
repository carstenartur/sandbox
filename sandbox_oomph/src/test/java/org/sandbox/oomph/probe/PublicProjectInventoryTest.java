/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.oomph.probe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class PublicProjectInventoryTest {
    @TempDir
    Path clone;

    @Test
    void includesDeclaredProjectsWithoutCommittedEclipseMetadata() throws Exception {
        pom("", "central", """
                <modules>
                  <module>sandbox-junit-migration-support</module>
                  <module>sandbox_junit_cleanup_jdt5</module>
                  <module>sandbox_junit_migration_support_test</module>
                  <module>sandbox_extra_search_help</module>
                  <module>container</module>
                </modules>
                """);
        pom("sandbox-junit-migration-support", "sandbox-junit-migration-support", "");
        pom("sandbox_junit_cleanup_jdt5", "sandbox_junit_cleanup_jdt5", "");
        pom("sandbox_junit_migration_support_test", "sandbox_junit_migration_support_test", "");
        pom("sandbox_extra_search_help", "sandbox_extra_search_help", "");
        pom("container", "aggregator", """
                <modules><module>nested/child/pom.xml</module></modules>
                """);
        pom("container/nested/child", "nested-artifact", """
                <parent><artifactId>parent-artifact</artifactId></parent>
                """);
        pom("sandbox_oomph", "sandbox_oomph", "");
        pom("sandbox_distribution_verify", "sandbox_distribution_verify", "");
        Map<Path, String> committed = Map.of(Path.of(".project"), "sandbox",
                Path.of("sandbox_target/.project"), "sandbox_target");

        assertEquals(Set.of("sandbox", "sandbox_target", "sandbox_oomph", "sandbox_extra_search_help",
                "sandbox-junit-migration-support", "sandbox_junit_cleanup_jdt5",
                "sandbox_junit_migration_support_test", "aggregator", "nested-artifact"),
                PublicProjectInventory.expectedProjects(clone, committed.keySet(), committed::get));
    }

    @Test
    void includesModuleDeclarationsFromInactiveProfiles() throws Exception {
        pom("", "central", """
                <profiles>
                  <profile>
                    <id>distribution</id>
                    <modules><module>sandbox_distribution_verify</module></modules>
                  </profile>
                  <profile>
                    <id>optional</id>
                    <activation><activeByDefault>false</activeByDefault></activation>
                    <modules><module>optional</module></modules>
                  </profile>
                </profiles>
                """);
        pom("sandbox_distribution_verify", "sandbox_distribution_verify", "");
        pom("optional", "optional-tool", """
                <profiles><profile><id>another-profile</id>
                  <modules><module>support</module></modules>
                </profile></profiles>
                """);
        pom("optional/support", "optional-support", "");
        pom("sandbox_oomph", "sandbox_oomph", "");
        pom("sandbox_extra_search_help", "sandbox_extra_search_help", "");

        assertEquals(Set.of("sandbox_distribution_verify", "optional-tool", "optional-support", "sandbox_oomph"),
                PublicProjectInventory.expectedProjects(clone, Set.of(), path -> {
                    throw new AssertionError("Unexpected description " + path);
                }));
    }

    @Test
    void honorsImportExclusionsAndIgnoresGeneratedProjectMetadata() throws Exception {
        pom("", "central", """
                <modules>
                  <module>.git/fixture</module>
                  <module>.github/fixture</module>
                  <module>target/fixture</module>
                  <module>sandbox_oomph/target/fixture</module>
                  <module>target-extra/visible</module>
                </modules>
                """);
        pom(".git/fixture", "git-fixture", "");
        pom(".github/fixture", "github-fixture", "");
        pom("target/fixture", "target-fixture", "");
        pom("sandbox_oomph/target/fixture", "oomph-target-fixture", "");
        pom("target-extra/visible", "visible", "");
        pom("unrelated", "unrelated", "");
        pom("sandbox_distribution_verify", "sandbox_distribution_verify", "");
        pom("sandbox_extra_search_help", "sandbox_extra_search_help", "");
        pom("sandbox_oomph", "sandbox_oomph", """
                <modules><module>nested</module></modules>
                """);
        pom("sandbox_oomph/nested", "standalone-nested", "");
        Files.writeString(clone.resolve("target-extra/visible/.project"), """
                <projectDescription><name>generated-name</name></projectDescription>
                """);
        Map<Path, String> committed = Map.of(Path.of(".project"), "sandbox",
                Path.of("other/non-maven/.project"), "committed-eclipse-project",
                Path.of(".git/fixture/.project"), "git-descriptor",
                Path.of(".github/fixture/.project"), "github-descriptor",
                Path.of("target/fixture/.project"), "target-descriptor",
                Path.of("sandbox_oomph/target/fixture/.project"), "oomph-target-descriptor");

        assertEquals(Set.of("sandbox", "sandbox_oomph", "visible", "committed-eclipse-project"),
                PublicProjectInventory.expectedProjects(clone, committed.keySet(), committed::get));
    }

    @Test
    void skipsExcludedMalformedMetadataBeforeLoadingIt() throws Exception {
        pom("", "central", "");
        pom("sandbox_oomph", "sandbox_oomph", "");
        Path excluded = description(".github/fixture", "<projectDescription>");
        Path included = description("example", "<projectDescription><name>example</name></projectDescription>");

        var projects = assertDoesNotThrow(
                () -> PublicProjectInventory.expectedProjects(clone, Set.of(excluded, included), this::loadDescription));
        assertEquals(Set.of("sandbox_oomph", "example"), projects);
    }

    @Test
    void propagatesParsingFailureForIncludedMalformedMetadata() throws Exception {
        pom("", "central", "");
        pom("sandbox_oomph", "sandbox_oomph", "");
        Path included = description("example", "<projectDescription>");

        assertThrows(SAXException.class,
                () -> PublicProjectInventory.expectedProjects(clone, Set.of(included), this::loadDescription));
    }

    private Path description(String folder, String contents) throws IOException {
        var directory = Files.createDirectories(clone.resolve(folder));
        return clone.relativize(Files.writeString(directory.resolve(".project"), contents));
    }

    private String loadDescription(Path path) throws Exception {
        var parser = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
        parser.setErrorHandler(new DefaultHandler());
        return parser.parse(clone.resolve(path).toFile()).getDocumentElement()
                .getElementsByTagName("name").item(0).getTextContent();
    }

    private void pom(String folder, String artifact, String declarations) throws IOException {
        var directory = Files.createDirectories(clone.resolve(folder));
        Files.writeString(directory.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <artifactId>%s</artifactId>
                  %s
                </project>
                """.formatted(artifact, declarations));
    }
}
