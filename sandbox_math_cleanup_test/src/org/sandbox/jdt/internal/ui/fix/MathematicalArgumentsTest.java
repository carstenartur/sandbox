package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MathematicalArgumentsTest {
 @Test void readOnlyIsDefaultAndAllLocationsAreRequired() {
  var args=MathematicalArguments.parse(new String[]{"--project","Example","--config","config.properties","--report","result.json"});
  assertEquals("Example",args.project());assertFalse(args.apply());assertFalse(args.acceptChecked());
  for(String[] invalid:new String[][]{{},{"--project","Example"},{"--project","","--config","c","--report","r"}})
   assertThrows(IllegalArgumentException.class,()->MathematicalArguments.parse(invalid));
 }
 @Test void flagsAreExplicitAndCannotBeDuplicatedOrInvented() {
  var args=MathematicalArguments.parse(new String[]{"--project","Example","--config","c","--report","r","--apply","--accept-checked"});
  assertTrue(args.apply());assertTrue(args.acceptChecked());
  for(String[] invalid:new String[][]{{"--project","Example","--config","c","--report","r","--apply","--apply"},
    {"--project","Example","--config","c","--report","r","--anything"},
    {"--project","Example","--project","Other","--config","c","--report","r"},
    {"--project","--config","c","--report","r"}})
   assertThrows(IllegalArgumentException.class,()->MathematicalArguments.parse(invalid));
 }
 @Test void configurationMustRemainSeparateFromReport() {
  assertThrows(IllegalArgumentException.class,()->MathematicalArguments.parse(new String[]{"--project","Example","--config","same","--report","./same"}));
 }
 @Test void projectIsOneWorkspaceNameAndPathsMayContainSpaces() {
  var args=MathematicalArguments.parse(new String[]{"--project","Example project","--config","a folder/options.properties","--report","a folder/report.json"});
  assertEquals(Path.of("a folder", "options.properties"),args.configuration());
  assertEquals(Path.of("a folder", "report.json"),args.report());
  assertThrows(IllegalArgumentException.class,()->MathematicalArguments.parse(new String[]{"--project","../Outside","--config","c","--report","r"}));
 }
}
