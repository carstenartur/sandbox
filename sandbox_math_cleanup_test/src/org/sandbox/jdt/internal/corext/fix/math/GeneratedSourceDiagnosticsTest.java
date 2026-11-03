/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.*;

class GeneratedSourceDiagnosticsTest {
    static Map<String,String> compiler() {
        var options = new HashMap<String,String>();
        JavaCore.setComplianceOptions("17", options);
        options.put(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK, JavaCore.WARNING);
        options.put(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
        return options;
    }
    static CompilationUnit parse(String source, Map<String,String> options) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray()); parser.setUnitName("Calculation.java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setCompilerOptions(options); parser.setResolveBindings(true);
        return (CompilationUnit) parser.createAST(null);
    }
    @Test void newlyIntroducedRealWarningIsRejected() {
        String before = "class Calculation { int f(int x) { return x; }}";
        String after = before.replace("return x", "return (int)x");
        var options = compiler();
        assertTrue(java.util.Arrays.stream(parse(after,options).getProblems()).anyMatch(IProblem::isWarning));
        var rejected = assertThrows(IllegalArgumentException.class, () -> MathematicalAnalysis.checkGeneratedSource(
                parse(before,options),before,after,options,17,null,null,before.indexOf("x;"),1,6));
        assertTrue(rejected.getMessage().startsWith("GENERATED_DIAGNOSTIC_INTRODUCED"),rejected.getMessage());
    }
    @Test void shiftedDuplicateWarningsSurviveMultipleActualEdits() throws Exception {
        String source="class Calculation { int f(int x) { return (int)x; } int g(int x) { return (int)x; }}";
        var edits=new MultiTextEdit(); edits.addChild(new InsertEdit(0,"// prefix\n"));
        edits.addChild(new InsertEdit(source.indexOf("int g"),"\n// between\n"));
        assertEquals(2,warnings(checked(source,edits)));
    }
    @Test void removedWarningCannotPayForIdenticalWarningInAnotherMethod() {
        String source="class Calculation { int f(int x) { return (int)x; } int g(int x) { return x; }}";
        var edits=new MultiTextEdit(); edits.addChild(new DeleteEdit(source.indexOf("(int)"),5));
        edits.addChild(new InsertEdit(source.lastIndexOf("return x")+7,"(int)"));
        introduced(source,edits);
    }
    @Test void removedWarningCannotPayForIdenticalWarningInSameMethod() {
        String source="class Calculation { int f(int x) { int a=(int)x; return x+a; }}";
        var edits=new MultiTextEdit(); edits.addChild(new DeleteEdit(source.indexOf("(int)"),5));
        edits.addChild(new InsertEdit(source.indexOf("return x")+7,"(int)"));
        introduced(source,edits);
    }
    @Test void opaqueEnvelopeCannotClaimInternallyCopiedWarning() {
        String source="class Calculation { int f(int x) { return (int)x; }}";
        introduced(source,new ReplaceEdit(source.indexOf("{ return"),source.length()-source.indexOf("{ return")-1,"{ if(x==0){} return (int)x; }"));
    }
    @Test void precisePlanPreservesWarningInsideLargerEnvelope() throws Exception {
        String source="class Calculation { int f(int x) { int a=x+0; int b=(int)x; return a+b; }}";
        int offset=source.indexOf("x+0");
        var plan=new JavaSourceRewritePlan("Calculation.java",JavaComputationTask.hash(source),source.length(),0,source.length(),
                List.of(),List.of(),Map.of(),List.of(new JavaSourceRewritePlan.Change(offset,3,JavaComputationTask.hash("x+0"),"x")));
        var generated=new Document(source); plan.newEdit(source).apply(generated);
        assertEquals(1,warnings(check(source,generated.get(),GeneratedSourceDiagnostics.edits(plan))));
    }
    @Test void sameOffsetCommentAndDeclarationInsertionsRetainFollowingWarning() throws Exception {
        String source="class Calculation { int f(int x) { return (int)x; }}";
        int offset=source.indexOf("return"); var edits=new MultiTextEdit();
        edits.addChild(new InsertEdit(offset,"// explanation\n")); edits.addChild(new InsertEdit(offset,"int a=x; x=a;\n"));
        assertTrue(warnings(checked(source,edits))>=1);
    }
    @Test void generatedErrorsAreUnconditionalIncludingBaselineErrors() {
        String source="class Calculation { int f() { return missing; }}";
        var rejected=assertThrows(IllegalArgumentException.class,()->check(source,source,List.of()));
        assertTrue(rejected.getMessage().startsWith("GENERATED_REGION_INVALID"));
    }
    @Test void unchangedReparseRetainsWarnings() {
        String source="class Calculation { int f(int x) { return (int)x; }}";
        assertEquals(1,warnings(check(source,source,List.of())));
    }
    @Test void shiftedNlsWarningRetainsActualLiteralRange() throws Exception {
        String source="class Calculation { String f() { return \"hello\"; }}";
        assertEquals(1,warnings(checked(source,new InsertEdit(0,"// first\n\n"))));
    }
    @Test void activeNlsRejectsStringConstructorAndRemovedMarker() {
        String source="class Calculation { String f() { return \"hello\"; //$NON-NLS-1$\n}}";
        assertEquals(0,warnings(parse(source,compiler())));
        introduced(source,new DeleteEdit(source.indexOf("//$NON-NLS-1$"),13));
        String numeric="class Calculation { Object f() { return null; }}";
        introduced(numeric,new ReplaceEdit(numeric.indexOf("null"),4,"new java.math.BigInteger(\"123\")"));
    }
    @Test void activeNlsRechecksMarkerNumberingAfterLiteralInsertion() {
        String source="class Calculation { String[] f() { return new String[]{\"a\"}; //$NON-NLS-1$\n}}";
        assertEquals(0,warnings(parse(source,compiler())));
        introduced(source,new InsertEdit(source.indexOf("\"a\""),"\"b\","));
    }
    @Test void incorrectEditDescriptorsCannotCreditUnchangedWarnings() {
        String source="class Calculation { int f(int x) { return (int)x; }}";
        assertThrows(IllegalArgumentException.class,()->check(source," "+source,List.of()));
        assertThrows(IllegalArgumentException.class,()->check(source,source,List.of(new GeneratedSourceDiagnostics.Edit(-1,0,0))));
        assertThrows(IllegalArgumentException.class,()->check(source,source,List.of(new GeneratedSourceDiagnostics.Edit(0,Integer.MAX_VALUE,0))));
        assertThrows(IllegalArgumentException.class,()->check(source,source,List.of(new GeneratedSourceDiagnostics.Edit(0,10,10),new GeneratedSourceDiagnostics.Edit(5,1,1))));
        assertThrows(IllegalArgumentException.class,()->GeneratedSourceDiagnostics.edits(new CopySourceEdit(0,1)));
    }
    @Test void explicitCompilerOptionsBindAnalysisAndRemainImmutable() {
        String source="class Calculation { int f(int x) { return (x+1)-1; }}";
        var compiler=compiler();
        var environment=new MathematicalAnalysis.SourceEnvironment("Calculation.java",List.of(),List.of(),List.of(),compiler);
        var options=new MathCleanUpOptions(true,java.util.Set.of(de.regelsuche.sdk.optimization.NumericKind.INT),
                de.regelsuche.sdk.optimization.SafetyProfile.PRESERVE_JAVA,de.regelsuche.sdk.optimization.OptimizationGoal.READABILITY,
                200000,5000,false,17,List.of());
        var result=MathematicalAnalysis.analyze(parse(source,compiler),source,options,null,-1,0,environment);
        assertTrue(result.changed(),result.diagnostics().toString());
        assertTrue(result.matches(source,compiler));
        compiler.put(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL,JavaCore.IGNORE);
        assertFalse(result.matches(source,compiler));
        assertEquals(JavaCore.WARNING,environment.compilerOptions().get(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL));
    }
    @Test void combinedEditsCannotIntroduceWarningAbsentFromEachCandidate() throws Exception {
        String source="class Calculation { int f() { int x=1; return x+x; }}";
        int first=source.indexOf("return x")+7,second=first+2;
        assertEquals(0,warnings(checked(source,new ReplaceEdit(first,1,"1"))));
        assertEquals(0,warnings(checked(source,new ReplaceEdit(second,1,"1"))));
        var combined=new MultiTextEdit(); combined.addChild(new ReplaceEdit(first,1,"1")); combined.addChild(new ReplaceEdit(second,1,"1"));
        introduced(source,combined);
    }
    @Test void actualCombinedDefaultExplanationsPreserveFinePlanWarningsAndUndo() throws Exception {
        String method="int f(int a,int b,int c) { int left=a*b+0+0+0; audit((int)a); // retained warning\nint right=a*c+0+0+0; return left+right; }";
        String source="class Calculation { static void audit(int x) {} "+method+method.replace("int f(","int g(")+"}";
        var compiler=compiler();var settings=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        settings.put(MathCleanUpOptions.CLEANUP,"true");settings.put(MathCleanUpOptions.KINDS,"INT");
        settings.put(MathCleanUpOptions.WORK_BUDGET,"1000000");settings.put(MathCleanUpOptions.MAX_STATES,"20000");
        var options=MathCleanUpOptions.parse(settings,17);
        assertEquals(MathCleanUpOptions.ExplanationMode.NONTRIVIAL,options.explanations());
        var environment=new MathematicalAnalysis.SourceEnvironment("Calculation.java",List.of(),List.of(),List.of(),compiler);
        var result=MathematicalAnalysis.analyze(parse(source,compiler),source,options,null,-1,0,environment);
        assertEquals(2,result.evidence().size(),result.diagnostics().toString());
        assertTrue(result.evidence().stream().allMatch(e->e.sourcePlan()!=null));
        var edit=result.newEdit();var provenance=GeneratedSourceDiagnostics.edits(edit);var document=new Document(source);
        var undo=edit.apply(document,TextEdit.CREATE_UNDO);
        assertTrue(document.get().contains("// Verified mathematics"),document.get());
        assertEquals(2,warnings(check(source,document.get(),provenance)));
        undo.apply(document);assertEquals(source,document.get());
        assertTrue(result.matches(source,compiler));
    }
    @Test void removingLastPrivateHelperUseDeclinesInsteadOfCreatingWarning() throws Exception {
        String source="class Calculation { private static int add(int a,int b){return a+b;} int f(int a,int b){return add(a,b)-a;}}";
        var options=new MathCleanUpOptions(true,java.util.Set.of(de.regelsuche.sdk.optimization.NumericKind.INT),
                de.regelsuche.sdk.optimization.SafetyProfile.PRESERVE_JAVA,de.regelsuche.sdk.optimization.OptimizationGoal.READABILITY,200000,5000,false,17,List.of());
        var result=MathematicalAnalysis.analyze(parse(source,compiler()),source,options,null,-1,0,
                new MathematicalAnalysis.SourceEnvironment("Calculation.java",List.of(),List.of(),List.of(),compiler()));
        assertFalse(result.changed(),result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d->d.message().startsWith("GENERATED_DIAGNOSTIC_INTRODUCED")),result.diagnostics().toString());
        var document=new Document(source);result.newEdit().apply(document);assertEquals(source,document.get());
    }
    private static CompilationUnit checked(String source,TextEdit edit) throws Exception {
        var provenance=GeneratedSourceDiagnostics.edits(edit);var document=new Document(source); edit.apply(document);
        return check(source,document.get(),provenance);
    }
    private static CompilationUnit check(String source,String generated,List<GeneratedSourceDiagnostics.Edit> edits) {
        var options=compiler();
        return MathematicalAnalysis.checkGeneratedSource(parse(source,options),source,generated,options,17,null,null,edits);
    }
    private static long warnings(CompilationUnit ast) { return java.util.Arrays.stream(ast.getProblems()).filter(IProblem::isWarning).count(); }
    private static void introduced(String source,TextEdit edit) {
        var rejected=assertThrows(IllegalArgumentException.class,()->checked(source,edit));
        assertTrue(rejected.getMessage().startsWith("GENERATED_DIAGNOSTIC_INTRODUCED"),rejected.toString());
    }
}
