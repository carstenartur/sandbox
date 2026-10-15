/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

class IncomingAssignmentBoundaryTest {
    @Test void aFragmentLimitCannotJoinAnIncomingWriteToReadsOfItsPreviousValue() {
        String source = "class Calculation { int run(int x) { int a=x+1; int c=x+2; int d=x+3; int e=x+4; "
                + "a=a+1; int b=a-1; return b; }}";
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName("Calculation.java"); parser.setSource(source.toCharArray()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true"); values.put(MathCleanUpOptions.KINDS, "INT");
        var regions = new JavaComputationExtractor().extractRuntimeFragments(ast, source, MathCleanUpOptions.parse(values, 17)).regions();
        var incoming = regions.stream().filter(r -> r.start() == source.indexOf("a=a+1;")).findFirst().orElseThrow();
        assertEquals("a=a+1;", source.substring(incoming.start(), incoming.start() + incoming.length()),
                "A retry must not emit 'a=a+1; return a' for a region whose return observes the incoming a");
    }
}
