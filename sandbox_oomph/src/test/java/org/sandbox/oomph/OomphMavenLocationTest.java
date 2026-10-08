/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.oomph;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

/** Selection contract; the existing native Oomph test still verifies all setup passes. */
class OomphMavenLocationTest {
    private static final String CLONE = "${git.clone.sandbox.location}";
    private static final String QUOTED_CLONE =
            "${git.clone.sandbox.location|path|trimTrailingSlashes|patternQuote}";
    private static final List<String> EXCLUDED = List.of(".git", ".github", "target", "sandbox_oomph/target");

    @Test
    void mavenDoesNotUseBackendIdentityForExclusions() throws Exception {
        for (Element locator : children(task("sandbox.maven"), "sourceLocator")) {
            assertTrue(children(locator, "excludedPath").isEmpty(),
                    "MavenImportTask.isExcluded dereferences a null relative URI across backend instances");
        }
        locationPredicate(); // The replacement must still be present, not merely remove exclusions.
    }

    @ParameterizedTest
    @ValueSource(strings = { "/work/sandbox", "/work/sandbox/", "C:\\work\\sandbox\\",
            "\\\\server\\share\\sandbox", "/work/A [1]+(x).ä$" })
    void excludedDirectoriesAndDescendantsRemainExcluded(String clone) throws Exception {
        Pattern pattern = exclusionPattern(clone);
        String root = portableRoot(clone);
        for (String directory : EXCLUDED) {
            for (String suffix : List.of("", "/project", "/nested/project", "/space and\nnewline")) {
                String location = root + "/" + directory + suffix;
                assertTrue(pattern.matcher(location).matches(), location);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "/work/sandbox", "C:\\work\\sandbox", "/work/A [1]+(x).ä$" })
    void ordinaryProjectsAndPrefixNeighboursRemainEligible(String clone) throws Exception {
        Pattern pattern = exclusionPattern(clone);
        String root = portableRoot(clone);
        for (String relative : List.of("", "sandbox_distribution_verify", "sandbox_oomph", "sandbox_target",
                "sandbox_math_cleanup", "sandbox-functional-converter-core", "target-extra/project",
                ".github-extra/project", ".git-extra/project", "sandbox_oomph/target-extra/project",
                "ordinary/target/project", "ordinary/.github/project", "ordinary/sandbox_oomph/target/project")) {
            String location = relative.isEmpty() ? root : root + "/" + relative;
            assertFalse(pattern.matcher(location).matches(), location);
        }
        assertFalse(pattern.matcher(root + "-other/target/project").matches());
        assertFalse(pattern.matcher("/unrelated/target/project").matches());
    }

    @Test
    void rootNameExclusionIsStillConjoinedWithLocationExclusion() throws Exception {
        Element predicate = only(children(primaryLocator(), "predicate"));
        assertEquals("predicates:AndPredicate", type(predicate));
        List<Element> operands = children(predicate, "operands");
        assertEquals(2, operands.size());
        List<String> leafTypes = new ArrayList<>();
        for (Element operand : operands) {
            assertEquals("predicates:NotPredicate", type(operand));
            Element leaf = only(children(operand, "operand"));
            leafTypes.add(type(leaf));
            if ("predicates:NamePredicate".equals(type(leaf))) {
                Pattern name = Pattern.compile(leaf.getAttribute("pattern"));
                assertTrue(name.matcher("central").matches());
                for (String project : List.of("sandbox", "central-helper", "sandbox_distribution_verify")) {
                    assertFalse(name.matcher(project).matches(), project);
                }
            }
        }
        assertEquals(List.of("predicates:NamePredicate", "predicates:LocationPredicate"), leafTypes);
    }

    @Test
    void eclipseImportKeepsItsTraversalExclusions() throws Exception {
        Element locator = only(children(task("sandbox.projects"), "sourceLocator"));
        assertEquals(EXCLUDED, children(locator, "excludedPath").stream().map(Element::getTextContent).toList());
        assertEquals(CLONE, locator.getAttribute("rootFolder"));
        assertEquals("true", locator.getAttribute("locateNestedProjects"));
    }

    @Test
    void bothMavenRootsAndNestedModuleDiscoveryArePreserved() throws Exception {
        List<Element> locators = children(task("sandbox.maven"), "sourceLocator");
        assertEquals(2, locators.size());
        assertEquals(CLONE, locators.get(0).getAttribute("rootFolder"));
        assertEquals("true", locators.get(0).getAttribute("locateNestedProjects"));
        assertEquals(CLONE + "/sandbox_oomph", locators.get(1).getAttribute("rootFolder"));
        assertTrue(children(locators.get(1), "predicate").isEmpty());
    }

    private Pattern exclusionPattern(String clone) throws Exception {
        String template = locationPredicate().getAttribute("pattern");
        assertTrue(template.contains(QUOTED_CLONE), "Normalize and quote the contributor's checkout path");
        // Same documented path / trimTrailingSlashes / patternQuote transformations as Oomph.
        return Pattern.compile(template.replace(QUOTED_CLONE, Pattern.quote(portableRoot(clone))));
    }

    private static String portableRoot(String clone) {
        return clone.replace('\\', '/').replaceAll("/+$", "");
    }

    private Element locationPredicate() throws Exception {
        Element predicate = only(children(primaryLocator(), "predicate"));
        assertEquals("predicates:AndPredicate", type(predicate));
        return children(predicate, "operands").stream()
                .filter(p -> "predicates:NotPredicate".equals(type(p)))
                .flatMap(p -> children(p, "operand").stream())
                .filter(p -> "predicates:LocationPredicate".equals(type(p)))
                .reduce((a, b) -> { throw new AssertionError("Duplicate location predicate"); })
                .orElseThrow(() -> new AssertionError("Missing location exclusion"));
    }

    private Element primaryLocator() throws Exception {
        return children(task("sandbox.maven"), "sourceLocator").get(0);
    }

    private static Element only(List<Element> elements) {
        assertEquals(1, elements.size());
        return elements.get(0);
    }

    private static List<Element> children(Element element, String tag) {
        List<Element> result = new ArrayList<>();
        var nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child && tag.equals(child.getTagName())) result.add(child);
        }
        return result;
    }

    private static String type(Element element) {
        return element.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
    }

    private static Element task(String id) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(Path.of("sandboxproject.setup").toFile());
        return children(document.getDocumentElement(), "setupTask").stream()
                .filter(t -> id.equals(t.getAttribute("id"))).findFirst().orElseThrow();
    }
}
