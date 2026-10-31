/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.tools.ToolProvider;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpProfileVersioner;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.text.edits.MalformedTreeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

/** Native JDT source snapshots and one explicit, atomic cleanup transaction. */
class FunctionalMathCleanUpTest {
    private static final String COMBINED="cleanup.loop_conversion.with_mathematics";
    private static final NullProgressMonitor MONITOR=new NullProgressMonitor();
    private IProject project;
    private IJavaProject java;
    @TempDir Path compiled;
    @BeforeEach void createProject() throws Exception {
        project=ResourcesPlugin.getWorkspace().getRoot().getProject("FunctionalMath-"+UUID.randomUUID());
        project.create(MONITOR);project.open(MONITOR);
        var description=project.getDescription();description.setNatureIds(new String[]{JavaCore.NATURE_ID});
        project.setDescription(description,MONITOR);
        var src=project.getFolder("src");src.create(true,true,MONITOR);
        var bin=project.getFolder("bin");bin.create(true,true,MONITOR);
        java=JavaCore.create(project);
        java.setRawClasspath(new IClasspathEntry[]{JavaCore.newSourceEntry(src.getFullPath()),
                JavaRuntime.getDefaultJREContainerEntry()},bin.getFullPath(),MONITOR);
        var compiler=java.getOptions(false);JavaCore.setComplianceOptions("17",compiler);java.setOptions(compiler);
    }
    @AfterEach void removeProject() throws Exception {if(project!=null&&project.exists())project.delete(true,true,MONITOR);}
    private ICompilationUnit unit(String source) throws Exception {
        return java.getPackageFragmentRoot(project.getFolder("src")).createPackageFragment("",true,MONITOR)
                .createCompilationUnit("Sample.java",source,true,MONITOR);
    }
    private static CompilationUnit parse(ICompilationUnit unit) {
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(unit);parser.setResolveBindings(true);
        var ast=(CompilationUnit)parser.createAST(MONITOR);
        for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());
        return ast;
    }
    private static Map<String,String> options() {
        var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP,"true");values.put(MathCleanUpOptions.KINDS,"INT,LONG");
        values.put(MathCleanUpOptions.WORK_BUDGET,"1000000");values.put(MathCleanUpOptions.EXPLANATIONS,"NONE");
        values.put(MathCleanUpOptions.TIMEOUT_SECONDS,"30");values.put(COMBINED,"true");
        values.put("cleanup.loop_conversion.enabled","true");values.put("cleanup.loop_conversion.target_format","stream");
        for(String source: new String[]{"enhanced_for","iterator_while","stream","classic_for"})
            values.put("cleanup.loop_conversion.from."+source,"true");
        return values;
    }
    private CompilationUnitChange change(ICompilationUnit unit,Map<String,String> options) throws Exception {
        var cleanup=new MathematicalCleanUpCore(options);
        assertFalse(cleanup.checkPreConditions(java,new ICompilationUnit[]{unit},MONITOR).hasFatalError());
        var fix=cleanup.createFix(new CleanUpContext(unit,parse(unit)));
        assertNotNull(fix,()->cleanup.checkPostConditions(MONITOR).toString());
        return fix.createChange(MONITOR);
    }
    private static final String SOURCE="""
        class Sample {
            int sum(int initial) { int total=initial; for(int i=1;i<=100;i++) total+=i; return total; }
            void show(java.util.List<String> values) { for(String value:values) { System.out.println(value); } }
        }
        """;
    @Test void mathematicsPrecedesStyleWithOnePreviewApplyAndUndo() throws Exception {
        var unit=unit(SOURCE);var change=change(unit,options());
        String preview=change.getPreviewContent(MONITOR);
        assertEquals(SOURCE,unit.getSource(),"Preparing every phase must leave the real buffer untouched");
        assertFalse(preview.contains("for(int i="),preview);
        assertTrue(preview.contains(".forEach("),preview);
        assertEquals(1,change.getTextEditChangeGroups().length,"The composed proof is one atomic selection");
        change.initializeValidationData(MONITOR);assertFalse(change.isValid(MONITOR).hasFatalError());
        var undo=change.perform(MONITOR);
        assertEquals(preview,unit.getSource());parse(unit);assertNotNull(undo);
        undo.perform(MONITOR);assertEquals(SOURCE,unit.getSource());
    }
    @Test void aVerifiedMapFusionGetsOneFreshWholeSumOpportunity() throws Exception {
        String source="class Sample { int run() { return java.util.stream.IntStream.range(0,100).map(x->x+1).map(y->y-1).sum(); } }";
        var unit=unit(source);var change=change(unit,options());String preview=change.getPreviewContent(MONITOR);
        assertFalse(preview.contains(".map("),preview);assertFalse(preview.contains(".sum()"),preview);
        assertEquals(source,unit.getSource());
        var undo=change.perform(MONITOR);parse(unit);undo.perform(MONITOR);assertEquals(source,unit.getSource());
    }
    @Test void freshSumReplayExhaustionRemainsADiagnosticNotAnUnhandledLimit() throws Exception {
        String source="class Sample { int first(){return java.util.stream.IntStream.range(0,100).sum();} int second(){return java.util.stream.IntStream.range(0,200).sum();} }";
        var unit=unit(source);var ast=parse(unit);var envelopes=new ArrayList<SourceRange>();
        ast.accept(new org.eclipse.jdt.core.dom.ASTVisitor(){
            @Override public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation call){
                if(call.getName().getIdentifier().equals("sum"))envelopes.add(new SourceRange(call.getStartPosition(),call.getLength()));return true;
            }
        });
        for(long allowance:new long[]{1000000,487000}) {
            var values=options();values.put(MathCleanUpOptions.WORK_BUDGET,Long.toString(allowance));
            try {
                var result=org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.analyzeFreshSums(ast,source,MathCleanUpOptions.parse(values,17),MONITOR,envelopes);
                if(allowance==1000000)assertEquals(2,result.replacements().size());
            } catch(IllegalArgumentException exhausted) {
                assertEquals(487000,allowance);assertTrue(exhausted.getMessage().contains("BUDGET_EXCEEDED"),exhausted.toString());
            }
        }
        assertEquals(source,unit.getSource());
    }
    @Test void ordinaryMathRemainsIndependentWithoutTheOptIn() throws Exception {
        var settings=options();settings.remove(COMBINED);
        var unit=unit(SOURCE);String preview=change(unit,settings).getPreviewContent(MONITOR);
        assertFalse(preview.contains("for(int i="),preview);
        assertTrue(preview.contains("for(String value:values)"),preview);
    }
    @Test void aConverterOnlyOpportunityStillProducesAnAtomicChange() throws Exception {
        String source="class Sample { void show(java.util.List<String> values) { for(String value:values) System.out.println(value); } }";
        var unit=unit(source);var result=change(unit,options());
        assertTrue(result.getPreviewContent(MONITOR).contains(".forEach("));
        assertEquals(source,unit.getSource());
        var undo=result.perform(MONITOR);parse(unit);undo.perform(MONITOR);assertEquals(source,unit.getSource());
    }
    @Test void earlierConvertedScopesKeepLaterMathematicalLocalBindings() throws Exception {
        String source="""
            public class Sample { public static long run(int n) {
                StringBuilder sink=new StringBuilder();
                for(String item:java.util.List.of("a","bb")) { sink.append(item); }
                int sum=n;
                for(int i=1;i<=100;i++) sum+=i;
                return sum+(long)sink.length();
            } }
            """;
        var unit=unit(source);String preview=change(unit,options()).getPreviewContent(MONITOR);
        assertFalse(preview.contains("for(String item:"),preview);assertFalse(preview.contains("for(int i="),preview);
        try(var before=compile(source);var after=compile(preview)) {
            for(int n:new int[]{Integer.MIN_VALUE,0,Integer.MAX_VALUE}) assertEquals(
                    before.loadClass("Sample").getMethod("run",int.class).invoke(null,n),
                    after.loadClass("Sample").getMethod("run",int.class).invoke(null,n));
        }
    }
    @Test void changedConverterOptionsInvalidateCopiedMergedLeaves() throws Exception {
        persist(options());
        var unit=unit(SOURCE);var change=change(unit,options());var copy=change.getEdit().copy();
        new ProjectScope(project).getNode(JavaUI.ID_PLUGIN).put("cleanup.loop_conversion.target_format","iterator_while");
        var document=new Document(SOURCE);
        assertThrows(MalformedTreeException.class,()->copy.apply(document));
        assertEquals(SOURCE,document.get());assertEquals(SOURCE,unit.getSource());
    }
    private void persist(Map<String,String> settings) throws Exception {
        var preferences=new ProjectScope(project).getNode(JavaUI.ID_PLUGIN);
        var defaults=JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getMap();
        defaults.forEach(preferences::put);settings.forEach(preferences::put);
        preferences.put(CleanUpConstants.CLEANUP_PROFILE,"_FunctionalMath_"+project.getName());
        preferences.putInt(CleanUpConstants.CLEANUP_SETTINGS_VERSION_KEY,new CleanUpProfileVersioner().getCurrentVersion());preferences.flush();
    }
    @Test void bothRegisteredCleanupsComposeOnceInEitherOrder() throws Exception {
        var unit=unit(SOURCE);
        for(boolean reverse:new boolean[]{false,true}) {
            var cleanups=JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps(Set.of(
                    "org.sandbox.jdt.ui.cleanup.mathematics","org.eclipse.jdt.ui.cleanup.functionalcleanup"));
            assertEquals(2,cleanups.length);
            if(reverse) {var first=cleanups[0];cleanups[0]=cleanups[1];cleanups[1]=first;}
            for(var cleanup:cleanups) {cleanup.setOptions(new MapCleanUpOptions(options()));cleanup.checkPreConditions(java,new ICompilationUnit[]{unit},MONITOR);}
            var deferred=new ArrayList<ICleanUp>();
            var change=CleanUpRefactoring.calculateChange(new CleanUpContext(unit,parse(unit)),cleanups,deferred,new HashSet<>());
            assertNotNull(change);assertTrue(deferred.isEmpty());assertEquals(1,change.getTextEditChangeGroups().length);
            var group=change.getTextEditChangeGroups()[0];group.setEnabled(false);assertEquals(SOURCE,change.getPreviewContent(MONITOR));group.setEnabled(true);
            String preview=change.getPreviewContent(MONITOR);assertTrue(preview.contains(".forEach("),preview);assertFalse(preview.contains("for(int i="));
            var undo=change.perform(MONITOR);assertEquals(preview,unit.getSource());undo.perform(MONITOR);assertEquals(SOURCE,unit.getSource());
        }
    }
    private CompilationUnitChange mergedChange(ICompilationUnit unit) throws Exception {
        var math=new MathematicalCleanUpCore(options());
        math.checkPreConditions(java,new ICompilationUnit[]{unit},MONITOR);
        var header=new org.eclipse.jdt.internal.ui.fix.AbstractCleanUp(){
            @Override public org.eclipse.jdt.ui.cleanup.ICleanUpFix createFix(CleanUpContext context){return monitor->{
                var change=new CompilationUnitChange("Header",unit);var edit=new org.eclipse.text.edits.InsertEdit(0,"// header\n");
                change.setEdit(edit);change.addTextEditGroup(new org.eclipse.text.edits.TextEditGroup("Header",edit));return change;
            };}
        };
        var deferred=new ArrayList<ICleanUp>();
        var context=new CleanUpContext(unit,parse(unit));
        var calculated=CleanUpRefactoring.calculateChange(context,new ICleanUp[]{math,header},deferred,new HashSet<>());
        assertNotNull(calculated);assertEquals(List.of(header),deferred,"JDT defers every overlapping whole-file edit, including boundary insertions");
        assertEquals(1,calculated.getTextEditChangeGroups().length);
        // Independently exercise the public native copy/reparent contract. This
        // is not a claim that calculateChange merged overlapping cleanup roots.
        var copied=calculated.getEdit().copy();var children=copied.removeChildren();assertEquals(1,children.length);
        var insertion=header.createFix(context).createChange(MONITOR).getEdit().copy();
        var root=new org.eclipse.text.edits.MultiTextEdit();root.addChildren(children);root.addChild(insertion);
        var aggregate=new CompilationUnitChange("Merged native changes",unit);aggregate.setEdit(root);
        aggregate.addTextEditGroup(new org.eclipse.text.edits.TextEditGroup(calculated.getName(),children));
        aggregate.addTextEditGroup(new org.eclipse.text.edits.TextEditGroup("Header",insertion));return aggregate;
    }
    @Test void deferredWholeFileChangeKeepsCopiedGroupsAndUndoUnderANewNativeRoot() throws Exception {
        var unit=unit(SOURCE);var aggregate=mergedChange(unit);assertEquals(2,aggregate.getTextEditChangeGroups().length);
        var group=Arrays.stream(aggregate.getTextEditChangeGroups()).filter(item->item.getName().contains("mathematics")).findFirst().orElseThrow();
        group.setEnabled(false);assertEquals("// header\n"+SOURCE,aggregate.getPreviewContent(MONITOR));group.setEnabled(true);
        String preview=aggregate.getPreviewContent(MONITOR);assertTrue(preview.startsWith("// header\n"));assertTrue(preview.contains(".forEach("));
        var detached=new org.eclipse.text.edits.MultiTextEdit();detached.addChildren(aggregate.getEdit().copy().removeChildren());
        var document=new Document(SOURCE);detached.apply(document);assertEquals(preview,document.get());assertEquals(SOURCE,unit.getSource());
        var undo=aggregate.perform(MONITOR);assertEquals(preview,unit.getSource());undo.perform(MONITOR);assertEquals(SOURCE,unit.getSource());
    }
    @Test void existingTargetsAndSourceFiltersRemainEffective() throws Exception {
        var unit=unit(SOURCE);var settings=options();settings.put("cleanup.loop_conversion.target_format","iterator_while");
        String preview=change(unit,settings).getPreviewContent(MONITOR);
        assertTrue(preview.contains(".iterator()"),preview);assertTrue(preview.contains(".hasNext()"),preview);assertFalse(preview.contains("for(int i="));
        settings.put("cleanup.loop_conversion.from.enhanced_for","false");
        preview=change(unit,settings).getPreviewContent(MONITOR);assertTrue(preview.contains("for(String value:"));
        String stream=SOURCE.replace("for(String value:values) { System.out.println(value); }","values.stream().forEach(value -> System.out.println(value));");
        unit=unit(stream);settings=options();settings.put("cleanup.loop_conversion.target_format","enhanced_for");
        preview=change(unit,settings).getPreviewContent(MONITOR);assertFalse(preview.contains(".forEach("),preview);assertTrue(preview.contains("for ("),preview);
    }
    @Test void defaultsAndOrdinaryConverterDoNotEnableMathematics() throws Exception {
        var registry=JavaPlugin.getDefault().getCleanUpRegistry();
        assertEquals("false",registry.getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getValue(COMBINED));
        assertFalse(registry.getDefaultOptions(CleanUpConstants.DEFAULT_SAVE_ACTION_OPTIONS).getKeys().contains(COMBINED));
        assertThrows(IllegalArgumentException.class,()->FunctionalMathOptions.validate(options(),MathCleanUpOptions.parse(options(),17),false));
        var settings=options();settings.put(MathCleanUpOptions.CLEANUP,"false");
        String source="class Sample { void show(java.util.List<String> values) { for(String value:values) System.out.println(value); } }";
        var unit=unit(source);var refactoring=new CleanUpRefactoring();refactoring.addCompilationUnit(unit);
        var cleanups=registry.createCleanUps(Set.of("org.sandbox.jdt.ui.cleanup.mathematics","org.eclipse.jdt.ui.cleanup.functionalcleanup"));
        assertEquals(2,cleanups.length);
        for(var cleanup:cleanups) {
            cleanup.setOptions(new MapCleanUpOptions(settings));
            assertFalse(cleanup.checkPreConditions(java,new ICompilationUnit[]{unit},MONITOR).hasFatalError(),cleanup.getClass().getName());
            refactoring.addCleanUp(cleanup);
        }
        var status=refactoring.checkFinalConditions(MONITOR);assertFalse(status.hasFatalError(),status.toString());
        var change=refactoring.createChange(MONITOR);assertNotNull(change);assertEquals(source,unit.getSource());
        var undo=change.perform(MONITOR);assertTrue(unit.getSource().contains(".forEach("));parse(unit);
        undo.perform(MONITOR);assertEquals(source,unit.getSource());
        assertDoesNotThrow(()->FunctionalMathOptions.validate(settings,MathCleanUpOptions.parse(settings,17),false));
        settings.put(MathCleanUpOptions.CLEANUP,"true");settings.put(MathCleanUpOptions.SAFETY,"GUARDED_FALLBACK");
        assertThrows(IllegalArgumentException.class,()->FunctionalMathOptions.validate(settings,MathCleanUpOptions.parse(settings,17),true));
        settings.put(MathCleanUpOptions.SAFETY,"PRESERVE_JAVA");settings.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN,"true");
        assertThrows(IllegalArgumentException.class,()->FunctionalMathOptions.validate(settings,MathCleanUpOptions.parse(settings,17),true));
    }
    @Test void cancellationBetweenMathematicsAndConversionLeavesThePrimaryUntouched() throws Exception {
        var unit=unit(SOURCE);
        var cancel=new NullProgressMonitor(){@Override public void subTask(String name){if(name.equals("Convert remaining loops"))setCanceled(true);}};
        var cleanup=new MathematicalCleanUpCore(options());cleanup.checkPreConditions(java,new ICompilationUnit[]{unit},cancel);
        try {assertThrows(OperationCanceledException.class,()->cleanup.createFix(new CleanUpContext(unit,parse(unit))));}
        finally {MathematicalAnalysisJob.cancelAllAndWait(Duration.ofSeconds(20));}
        assertEquals(SOURCE,unit.getSource());
    }
    @Test void exhaustedAggregateAllowanceProducesNoPartialFix() throws Exception {
        var unit=unit(SOURCE);var settings=options();settings.put(MathCleanUpOptions.WORK_BUDGET,"1");
        var cleanup=new MathematicalCleanUpCore(settings);cleanup.checkPreConditions(java,new ICompilationUnit[]{unit},MONITOR);
        assertNull(cleanup.createFix(new CleanUpContext(unit,parse(unit))));assertEquals(SOURCE,unit.getSource());
        assertTrue(cleanup.checkPostConditions(MONITOR).toString().contains("COMBINED_BUDGET_EXCEEDED"));
    }
    @Test void dependencyChangesAfterPrivateCopiesAreDiscardedStillInvalidatePreview() throws Exception {
        var pack=java.getPackageFragmentRoot(project.getFolder("src")).getPackageFragment("");
        var helper=pack.createCompilationUnit("Helper.java","class Helper { static int add(int x,int y){ return x+y; } }",true,MONITOR);
        String source="class Sample { int run(int x,int y,int[] z) { int a=Helper.add(x,y); z[0]=a; return a-x; } }";
        var unit=unit(source);var settings=options();settings.put(MathCleanUpOptions.MAX_STATES,"20000");
        var change=change(unit,settings);assertTrue(change.getPreviewContent(MONITOR).contains("return y;"));
        var copied=change.getEdit().copy();helper.getBuffer().setContents(helper.getSource().replace("x+y","x-y"));
        var document=new Document(source);assertThrows(MalformedTreeException.class,()->copied.apply(document));assertEquals(source,document.get());
    }
    @Test void protectedTextAndBindingsAreCheckedAfterNativeEditEvents() throws Exception {
        String source="class Sample { static int value=1; int run(){ return value+1; } }";
        var unit=unit(source);int start=source.indexOf("value+1");
        var snapshot=new FunctionalMathSnapshot(parse(unit),source,List.of(new SourceRange(start,7)),work->{});
        var document=new Document(source);document.addDocumentListener(snapshot);
        document.replace(start,7,"value+2");
        var changedText=snapshot;var rejected=document;
        assertThrows(IllegalArgumentException.class,()->changedText.verify(parse(unit),rejected));assertEquals(source,unit.getSource());
        snapshot=new FunctionalMathSnapshot(parse(unit),source,List.of(new SourceRange(start,7)),work->{});
        document=new Document(source);document.addDocumentListener(snapshot);
        document.replace(source.indexOf("return"),0,"int value=2; ");unit.getBuffer().setContents(document.get());
        var changedBinding=snapshot;var changedDocument=document;
        assertThrows(IllegalArgumentException.class,()->changedBinding.verify(parse(unit),changedDocument));
    }
    @Test void copiedGuardRejectsAnUnrelatedSameLengthDocumentBeforeAnyMutation() throws Exception {
        var unit=unit(SOURCE);var copied=change(unit,options()).getEdit().copy();
        String other=SOURCE.replace("initial","another");assertEquals(SOURCE.length(),other.length());
        var document=new Document(other);assertThrows(MalformedTreeException.class,()->copied.apply(document));
        assertEquals(other,document.get());assertEquals(SOURCE,unit.getSource());
    }
    @Test void nativeFileBufferSuffixInvalidatesCopiedMergedLeavesAndPerformOperation() throws Exception {
        var unit=unit(SOURCE);var buffers=FileBuffers.getTextFileBufferManager();
        buffers.connect(unit.getPath(),LocationKind.IFILE,MONITOR);unit.becomeWorkingCopy(MONITOR);
        try {
            var document=buffers.getTextFileBuffer(unit.getPath(),LocationKind.IFILE).getDocument();
            var aggregate=mergedChange(unit);aggregate.initializeValidationData(MONITOR);
            var copied=aggregate.getEdit().copy();String suffix="// appended editor text\n";
            document.replace(document.getLength(),0,suffix);
            assertEquals(SOURCE+suffix,unit.getSource(),"The native primary CU reads the actual connected FileBuffer document");
            assertThrows(MalformedTreeException.class,()->copied.apply(document));
            assertEquals(SOURCE+suffix,document.get(),"No header or combined replacement may run before validation");
            var operation=new PerformChangeOperation(aggregate);operation.run(MONITOR);
            assertFalse(operation.changeExecuted());assertTrue(operation.getValidationStatus().hasFatalError());
            assertEquals(SOURCE+suffix,document.get());assertEquals(SOURCE+suffix,unit.getSource());
        } finally {unit.discardWorkingCopy();buffers.disconnect(unit.getPath(),LocationKind.IFILE,MONITOR);}
    }
    @Test void numericalExclusionsDoNotDisableTheSeparateStyleProfile() throws Exception {
        var settings=options();settings.put(MathCleanUpOptions.EXCLUSIONS,"**/Sample.java");
        var unit=unit(SOURCE);String preview=change(unit,settings).getPreviewContent(MONITOR);
        assertFalse(preview.contains("5050"),preview);assertTrue(preview.contains(".forEach("),preview);
        parse(unit(preview)); // Enabled classic-loop admission must not introduce an illegal mutable capture.
    }
    @Test void cancelledApplyAndDirtyPrimaryWorkingCopiesKeepTheirExactOriginal() throws Exception {
        var unit=unit(SOURCE);unit.becomeWorkingCopy(MONITOR);
        String dirty=SOURCE+"// unsaved editor text\n";
        try {
            unit.getBuffer().setContents(dirty);var change=change(unit,options());
            String preview=change.getPreviewContent(MONITOR);assertEquals(dirty,unit.getSource());
            var cancelled=new NullProgressMonitor();cancelled.setCanceled(true);
            assertThrows(OperationCanceledException.class,()->change.perform(cancelled));assertEquals(dirty,unit.getSource());
            var undo=change.perform(MONITOR);assertEquals(preview,unit.getSource());undo.perform(MONITOR);assertEquals(dirty,unit.getSource());
        } finally {unit.discardWorkingCopy();}
    }
    @Test void listenerBudgetVetoSurvivesJFaceSafeRunner() throws Exception {
        String source="class Sample { int run(int value){ return value+1; } }";
        var unit=unit(source);var rejectNext=new java.util.concurrent.atomic.AtomicBoolean();
        var snapshot=new FunctionalMathSnapshot(parse(unit),source,List.of(new SourceRange(source.indexOf("value+1"),7)),work->{
            if(rejectNext.getAndSet(false))throw new IllegalArgumentException("exhausted event allowance");
        });
        var document=new Document(source);document.addDocumentListener(snapshot);rejectNext.set(true);document.replace(0,0,"// comment\n");
        assertThrows(IllegalArgumentException.class,()->snapshot.verify(parse(unit),document));assertEquals(source,unit.getSource());
    }
    @Test void protectedVerificationCopiesSourceOnceAndChargesItsTraversal() throws Exception {
        String source="class Sample { int run(int left,int right){ return left+right+left; } }";
        var unit=unit(source);var calls=new java.util.concurrent.atomic.AtomicInteger();
        var snapshot=new FunctionalMathSnapshot(parse(unit),source,List.of(new SourceRange(source.indexOf("left+right"),15)),work->calls.incrementAndGet());
        var document=new Document(source){int reads;@Override public String get(){reads++;return super.get();}};
        document.addDocumentListener(snapshot);document.replace(0,0,"// changed gap buffer\n");
        unit.getBuffer().setContents("// changed gap buffer\n"+source);calls.set(0);
        snapshot.verify(parse(unit),document);
        assertAll(()->assertEquals(1,document.reads,"Read the complete edited document only once"),
                ()->assertTrue(calls.get()>snapshot.exclusions().size(),"Charge and poll during index construction and receipt replay"));
    }
    @Test void protectedVerificationCanCancelDuringReplayWithoutTouchingTheUnit() throws Exception {
        String source="class Sample { int run(int value){ return value+value+value; } }";
        var unit=unit(source);var checks=new java.util.concurrent.atomic.AtomicInteger();
        var replay=new java.util.concurrent.atomic.AtomicBoolean();
        var snapshot=new FunctionalMathSnapshot(parse(unit),source,List.of(new SourceRange(source.indexOf("value+value"),17)),work->{
            if(replay.get() && checks.incrementAndGet()==3)throw new OperationCanceledException("cancel verification");
        });
        var ast=parse(unit);replay.set(true);
        assertThrows(OperationCanceledException.class,()->snapshot.verify(ast,new Document(source)));
        assertEquals(3,checks.get());assertEquals(source,unit.getSource());
    }
    @Test void repeatedCombinedCleanupPreservesPreviouslyGeneratedRuntimeTemplates() throws Exception {
        String source="class Sample { int run(int n){ int sum=17; for(int i=0;i<n;i++) sum+=i*i; return sum; } }";
        var settings=options();settings.put(MathCleanUpOptions.WORK_BUDGET,"10000000");
        var unit=unit(source);String generated=change(unit,settings).getPreviewContent(MONITOR);assertTrue(generated.contains("$series"));
        unit=unit(generated);var cleanup=new MathematicalCleanUpCore(settings);
        cleanup.checkPreConditions(java,new ICompilationUnit[]{unit},MONITOR);
        var repeated=cleanup.createFix(new CleanUpContext(unit,parse(unit)));
        if(repeated!=null) assertEquals(generated,repeated.createChange(MONITOR).getPreviewContent(MONITOR),"Generated guards and original fallbacks must not acquire nested fast paths");
        assertEquals(generated,unit.getSource());
        String withSibling=generated.replace(" return sum;"," int added=0;for(int k=1;k<=100;k++)added+=k;return sum+added;");
        String withNewLoop=withSibling.substring(0,withSibling.lastIndexOf('}'))
                +"int extra(int n){int sum=n;for(int j=1;j<=100;j++)sum+=j;return sum;} }";
        unit=unit(withNewLoop);String preview=change(unit,settings).getPreviewContent(MONITOR);
        assertFalse(preview.contains("for(int j="),preview);assertFalse(preview.contains("for(int k="),preview);
        String template=generated.substring(generated.indexOf("{\n"),generated.indexOf(" return sum;"));
        assertTrue(preview.contains(template),"Existing template remains byte-for-byte while independent mathematics still improves");
    }
    @Test void repeatedCombinedCleanupPreservesTheEarlyGuardInAStreamSupplier() throws Exception {
        String source="class Sample { int run(int n){return java.util.stream.IntStream.range(0,n).map(i->i*i).sum();} }";
        var settings=options();settings.put(MathCleanUpOptions.WORK_BUDGET,"10000000");
        var unit=unit(source);String generated=change(unit,settings).getPreviewContent(MONITOR);
        assertTrue(generated.contains("new java.util.function.IntSupplier"),generated);assertTrue(generated.contains("if(((long)"),generated);
        unit=unit(generated);var cleanup=new MathematicalCleanUpCore(settings);
        cleanup.checkPreConditions(java,new ICompilationUnit[]{unit},MONITOR);
        var repeated=cleanup.createFix(new CleanUpContext(unit,parse(unit)));
        if(repeated!=null)assertEquals(generated,repeated.createChange(MONITOR).getPreviewContent(MONITOR),"Both supplier fallbacks and their early guard remain unchanged");
        assertEquals(generated,unit.getSource());
    }
    @Test void changedMathSettingsAndClasspathInvalidateCopiedLeaves() throws Exception {
        persist(options());var unit=unit(SOURCE);var copied=change(unit,options()).getEdit().copy();
        new ProjectScope(project).getNode(JavaUI.ID_PLUGIN).put(MathCleanUpOptions.WORK_BUDGET,"999999");
        var document=new Document(SOURCE);var staleOptions=copied;
        assertThrows(MalformedTreeException.class,()->staleOptions.apply(document));assertEquals(SOURCE,document.get());
        persist(options());copied=change(unit,options()).getEdit().copy();
        var extra=project.getFolder("extra");extra.create(true,true,MONITOR);
        var entries=new ArrayList<>(List.of(java.getRawClasspath()));entries.add(JavaCore.newSourceEntry(extra.getFullPath()));
        java.setRawClasspath(entries.toArray(IClasspathEntry[]::new),MONITOR);
        var staleClasspath=copied;assertThrows(MalformedTreeException.class,()->staleClasspath.apply(document));assertEquals(SOURCE,document.get());
    }
    @Test void changedSourceAndCompilerNeverPartiallyApply() throws Exception {
        var unit=unit(SOURCE);var change=change(unit,options());var copy=change.getEdit().copy();
        java.setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL,JavaCore.ERROR);
        var document=new Document(SOURCE);
        var compilerCopy=copy;
        assertThrows(MalformedTreeException.class,()->compilerCopy.apply(document));assertEquals(SOURCE,document.get());
        java.setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL,JavaCore.WARNING);
        change=change(unit,options());copy=change.getEdit().copy();
        String newer=SOURCE+"// external change\n";unit.getBuffer().setContents(newer);
        var staleCopy=copy;var newerDocument=new Document(newer);
        assertThrows(MalformedTreeException.class,()->staleCopy.apply(newerDocument));assertEquals(newer,newerDocument.get());
    }
    @Test void runtimeTemplatesStayIntactAndBothPathsExecuteEquivalently() throws Exception {
        String source="""
            public class Sample {
                public static long run(int n) {
                    int sum=17;
                    for(int i=0;i<n;i++) sum+=i*i;
                    StringBuilder sink=new StringBuilder();
                    for(String item:java.util.List.of("a","bb","ccc")) { sink.append(item); }
                    return sum+(long)sink.length();
                }
            }
            """;
        var unit=unit(source);String preview=change(unit,options()).getPreviewContent(MONITOR);
        assertTrue(preview.contains("$series"),preview);
        assertTrue(preview.contains("for(int i=0;i<n;i++) sum+=i*i;"),"The original numerical fallback remains byte-for-byte");
        assertFalse(preview.contains("for(String item:"),preview);
        try(var before=compile(source);var after=compile(preview)) {
            for(int n:new int[]{-10,0,1,100,50000})assertEquals(
                    before.loadClass("Sample").getMethod("run",int.class).invoke(null,n),
                    after.loadClass("Sample").getMethod("run",int.class).invoke(null,n),"n="+n);
        }
        assertEquals(source,unit.getSource());
    }
    private URLClassLoader compile(String source) throws Exception {
        Path folder=Files.createDirectory(compiled.resolve(UUID.randomUUID().toString()));
        Path file=folder.resolve("Sample.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",folder.toString(),file.toString()));
        return new URLClassLoader(new java.net.URL[]{folder.toUri().toURL()},null);
    }
}
