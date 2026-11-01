/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.SemanticAssumption.Kind;

/** Facts must be derived from actual control flow and immutable primitive bindings. */
class PrimitiveSourceGuardTest {
    @TempDir Path temporary;

    @Test void projectsNonnegativeFactsFromTrueBranchesAndFalseDisjunctionGuards() {
        for (String body : List.of("if (x >= 0) { return 3-(x%4); } return 0;",
                "if (x < 0 || y < 0) throw new IllegalArgumentException(); return 3-(x%4);")) {
            var regions = extract("class Sample { int f(int x,int y) { " + body + " } }");
            assertTrue(regions.stream().anyMatch(r -> nonnegative(r, "x")), body);
        }
    }

    @Test void followsActualStaticThrowingHelperChainsAndRejectsEarlyReturnLookalikes() {
        for (boolean throwing : List.of(true, false)) {
            String source = "class Sample { static void error(int code) { stop(code); } static void stop(int code) { "
                    + (throwing ? "throw new IllegalArgumentException();" : "if(code==1) return; throw new IllegalArgumentException();")
                    + " } int f(int x,int y) { if(x < 0 || y < 0) error(1); return 3-(x%4); } }";
            var regions = extract(source);
            assertEquals(throwing, regions.stream().anyMatch(r -> nonnegative(r, "x")), source);
        }
    }

    @Test void rejectsMutableLocalsNonDominatingGuardsAndFalseConjunctions() {
        for (String body : List.of("if(x<0) throw new IllegalArgumentException(); x--; return 3-(x%4);",
                "if(y>0) { if(x<0) throw new IllegalArgumentException(); } return 3-(x%4);",
                "if(x<0 && y<0) throw new IllegalArgumentException(); return 3-(x%4);")) {
            assertFalse(extract("class Sample { int f(int x,int y) { " + body + " } }").stream()
                    .anyMatch(r -> nonnegative(r, "x")), body);
        }
    }

    @Test void admitsTraditionalSwitchInitializersWithoutMovingTheirEvaluation() {
        String source = "class Sample { int f(int x,int mode) { if(x<0) throw new IllegalArgumentException(); "
                + "switch(mode) { case 2: int shift = 3-(x%4); return shift; default: return 0; } } }";
        var regions = extract(source);
        assertTrue(regions.stream().anyMatch(r -> source.substring(r.start(), r.start()+r.length()).equals("3-(x%4)")
                && nonnegative(r, "x")), regions.toString());
    }

    @Test void sourceHelperDependenciesInvalidateTheirReceipt() throws Exception {
        Path helper = temporary.resolve("Helper.java");
        Files.writeString(helper, "class Helper { static void error() { throw new IllegalArgumentException(); } }");
        String source = "class Sample { int f(int x) { if(x<0) Helper.error(); return 3-(x%4); } }";
        var environment = new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(temporary.toString()), List.of());
        var ast = parse(source, environment);
        JavaSourceContext.Checkpoint checkpoint;
        try (var context = JavaSourceContext.open(ast, source, environment, new NullProgressMonitor(), options())) {
            assertTrue(refined(ast, source).stream().anyMatch(r -> nonnegative(r, "x")));
            checkpoint = context.checkpoint();
            assertFalse(checkpoint.receipts().isEmpty());
            assertTrue(checkpoint.current());
        }
        Files.writeString(helper, "class Helper { static void error() { return; } }");
        assertFalse(checkpoint.current(), "Changed throwing helper source invalidates Apply");
        var fresh = parse(source, environment);
        try (var context = JavaSourceContext.open(fresh, source, environment, new NullProgressMonitor(), options())) {
            assertFalse(refined(fresh, source).stream().anyMatch(r -> nonnegative(r, "x")));
        }
    }

    @Test void deferredProofUsesOnlyTheActualRemainderIncludingHelperDiscovery() throws Exception {
        Files.writeString(temporary.resolve("Helper.java"),
                "class Helper { static void error() { throw new IllegalArgumentException(); } }");
        String source = "class Sample { int f(int x) { if(x<0) Helper.error(); return 3-(x%4); } }";
        var environment = new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(temporary.toString()), List.of());
        for (long remaining : new long[] {0, 1, 16, 64}) {
            var ast = parse(source, environment); var monitor = new NullProgressMonitor();
            try (var context = JavaSourceContext.open(ast, source, environment, monitor, options())) {
                context.deferPrimitiveGuards(true);
                var region = new JavaComputationExtractor().extractRuntime(ast, source, options()).regions().getFirst();
                assertFalse(nonnegative(region, "x"));
                assertEquals(0, context.takeWork(), "Initial extraction must leave new primitive discovery for its later phase");
                var common = new JavaValueSourceSummary.Work(2000, options(), () -> false);
                JavaSourceContext.valueWork(ast, common);
                try {
                    context.refinePrimitives(region, remaining);
                    assertTrue(common.used <= remaining, "An active common meter cannot spend beyond the scoped source remainder: "
                            + common.used + " > " + remaining);
                } finally { JavaSourceContext.valueWork(ast, null); }
                assertTrue(context.takeWork() <= remaining, "Transitive source lookup shares the remaining allowance");
                assertTrue(nonnegative(context.refinePrimitives(region, 1000), "x"));
                context.takeWork(); monitor.setCanceled(true);
                assertSame(region, context.refinePrimitives(region, 1000));
                assertEquals(0, context.takeWork());
            }
        }
    }

    @Test void sourceFactDiscoveryStopsAtItsSharedAllowanceAndCancellation() {
        String source="class Sample { int f(int x) { if(x<0) throw new IllegalArgumentException(); return 3-(x%4); } }";
        for(boolean cancelled:List.of(false,true)) {
            var ast=parse(source,null); var monitor=new NullProgressMonitor(); monitor.setCanceled(cancelled);
            try(var context=JavaSourceContext.open(ast,source,null,monitor,options(),cancelled?1000:1)) {
                assertFalse(refined(ast,source).stream().anyMatch(r->nonnegative(r,"x")));
                assertTrue(context.takeWork()<= (cancelled?0:1));
            }
        }
    }
    @Test void findingARegionAlsoChargesTheSkippedSourceSiblings() {
        var source=new StringBuilder("class Sample {");
        for(int i=0;i<500;i++) source.append("void m").append(i).append("() {}");
        source.append("int f(int x) { if(x<0) throw new IllegalArgumentException(); return 3-(x%4); } }");
        var ast=parse(source.toString(),null);
        try(var context=JavaSourceContext.open(ast,source.toString(),null,new NullProgressMonitor(),options(),100)) {
            assertFalse(refined(ast,source.toString()).stream().anyMatch(r->nonnegative(r,"x")),
                    "Finding a late region may not bypass metering of preceding sibling nodes");
        }
    }

    private static boolean nonnegative(JavaComputationRegion region, String name) {
        return region.assumptions().stream().anyMatch(a -> a.kind() == Kind.NON_NEGATIVE && name.equals(region.inputNames().get(a.subject())));
    }
    private static List<JavaComputationRegion> extract(String source) {
        var ast = parse(source, null);
        try (var context = JavaSourceContext.open(ast, source, null, new NullProgressMonitor(), options())) {
            return refined(ast, source);
        }
    }
    private static List<JavaComputationRegion> refined(CompilationUnit ast, String source) {
        var extractor = new JavaComputationExtractor();
        var regions = new java.util.ArrayList<>(extractor.extractRuntime(ast, source, options()).regions());
        regions.addAll(extractor.extractRuntimeContexts(ast, source, options()).regions());
        return regions.stream().map(r -> JavaSourceGuardRefinement.refine(r, ast)).toList();
    }
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    private static CompilationUnit parse(String source, MathematicalAnalysis.SourceEnvironment environment) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], environment == null ? new String[0] : environment.sourcepath().toArray(String[]::new), null, true);
        parser.setResolveBindings(true);
        var settings = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit)parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
}
