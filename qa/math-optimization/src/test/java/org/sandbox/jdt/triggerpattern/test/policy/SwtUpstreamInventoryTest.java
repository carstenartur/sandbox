/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** The two official Linux SWT test modules must actually execute tests. */
class SwtUpstreamInventoryTest {
    private static final List<String> MODULES=List.of("org.eclipse.swt.tests", "org.eclipse.swt.tests.gtk");
    @TempDir Path temporary;

    @Test void aMissingModuleCannotPassByMatchingTheRemainingInventory() throws Exception {
        for(String phase:List.of("baseline", "generated")) report(phase,MODULES.getFirst(),false);
        assertThrows(AssertionError.class,()->verify(temporary.resolve("baseline"),temporary.resolve("generated"),temporary.resolve("review")));
    }

    @Test void aSkippedOnlyModuleCannotPassByMatchingItsSkippedTests() throws Exception {
        for(String phase:List.of("baseline", "generated")) {
            report(phase,MODULES.getFirst(),false);
            report(phase,MODULES.getLast(),true);
        }
        assertThrows(AssertionError.class,()->verify(temporary.resolve("baseline"),temporary.resolve("generated"),temporary.resolve("review")));
    }

    @Test void bothModulesExecuteAndPreserveTheirTestStates() throws Exception {
        for(String phase:List.of("baseline", "generated")) for(String module:MODULES) report(phase,module,false);
        verify(temporary.resolve("baseline"),temporary.resolve("generated"),temporary.resolve("review"));
    }

    @Test
    @EnabledIfSystemProperty(named="sandbox.math.swt.upstream",matches="true")
    void configuredSwtModulesPreserveExecutedTests() throws Exception {
        verify(property("sandbox.junit.inventory.baseline"),property("sandbox.junit.inventory.migrated"),
                property("sandbox.junit.inventory.output").resolveSibling("required-swt-modules"));
    }

    private static void verify(Path baseline,Path generated,Path output) throws Exception {
        for(String module:MODULES) {
            Path relative=Path.of("tests",module);
            var comparison=JUnitXmlInventoryComparator.compare(baseline.resolve(relative),generated.resolve(relative),
                    JUnitXmlInventoryComparator.Mapping.empty());
            comparison.write(output.resolve(module+".json"));
            assertTrue(comparison.passed(),module+": "+comparison.problems());
            assertTrue(comparison.baseline().passed()>0,module+" must execute passing tests before cleanup");
            assertTrue(comparison.migrated().passed()>0,module+" must execute passing tests after cleanup");
        }
    }

    private static Path property(String name) {
        String value=System.getProperty(name);
        assertNotNull(value,"Required evidence property: "+name);
        assertFalse(value.isBlank(),"Required evidence property: "+name);
        return Path.of(value);
    }

    private void report(String phase,String module,boolean skipped) throws Exception {
        Path directory=Files.createDirectories(temporary.resolve(phase).resolve("tests").resolve(module).resolve("target/surefire-reports"));
        Files.writeString(directory.resolve("TEST-suite.xml"),"<testsuite><testcase classname='"+module+".Example' name='executes'>"
                +(skipped?"<skipped/>":"")+"</testcase></testsuite>");
    }
}
