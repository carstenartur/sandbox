/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Runs in the converter-only reactor, which does not install the optional math host. */
class FunctionalMathIndependenceTest {
 @RegisterExtension AbstractEclipseJava context=new EclipseJava22();
 @Test void staleCombinedProfileNeverSuppressesTheStandaloneConverter() throws Exception {
  assertNull(Platform.getBundle("sandbox_math_cleanup"));
  String source="package test1; class Sample { void run(java.util.List<String> values){ for(String value:values) System.out.println(value); } }";
  var unit=context.getSourceFolder().createPackageFragment("test1",false,null).createCompilationUnit("Sample.java",source,false,null);
  var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(unit);parser.setResolveBindings(true);
  var ast=(CompilationUnit)parser.createAST(null);
  var cleanup=new UseFunctionalCallCleanUpCore(Map.of("cleanup.functionalloop","true",
    "cleanup.mathematics","true","cleanup.loop_conversion.with_mathematics","true"));
  var fix=cleanup.createFix(new CleanUpContext(unit,ast));assertNotNull(fix);
  String preview=fix.createChange(new NullProgressMonitor()).getPreviewContent(new NullProgressMonitor());
  assertTrue(preview.contains(".forEach("),preview);assertEquals(source,unit.getSource());
 }
}
