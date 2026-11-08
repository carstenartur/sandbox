/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Arrays;
import java.util.UUID;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;

/** Native Eclipse/JDT lifecycle, including guarded copies of the multipart edit. */
class MathematicalSliceChangeTest {
    @ParameterizedTest @CsvSource({"false,warning", "true,warning", "false,error"})
    void previewApplyUndoAndStaleCopiedEditUseTheSameAtomicSourcePlan(boolean diagnosticsEnabled, String castSeverity) throws Exception {
        var monitor=new NullProgressMonitor();
        IProject project=ResourcesPlugin.getWorkspace().getRoot().getProject("MathSlice-"+UUID.randomUUID());
        try {
            project.create(monitor);project.open(monitor);
            var description=project.getDescription();description.setNatureIds(new String[]{JavaCore.NATURE_ID});
            project.setDescription(description,monitor);
            var folder=project.getFolder("src");folder.create(true,true,monitor);
            var bin=project.getFolder("bin");bin.create(true,true,monitor);
            var java=JavaCore.create(project);
            java.setRawClasspath(new IClasspathEntry[]{JavaCore.newSourceEntry(folder.getFullPath()),
                    JavaRuntime.getDefaultJREContainerEntry()},bin.getFullPath(),monitor);
            var compilerOptions=StrictCompilerOptions.strengthen(diagnosticsEnabled
                    ? StrictCompilerOptions.allWarnings(17) : java.getOptions(true));
            JavaCore.setComplianceOptions("17",compilerOptions);
            compilerOptions.put(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK,castSeverity);
            if(diagnosticsEnabled) {
                compilerOptions.put(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL,JavaCore.WARNING);
            }
            java.setOptions(compilerOptions);var projectOptions=java.getOptions(true);
            String source="class Sample { static void audit(){} int f(int a,int b,int c) {\n"
                    +"int left=a*b;\naudit(); // unchanged effect\nint right=a*c;\nreturn left+right;\n}}";
            if(diagnosticsEnabled) {
                source=source.replace("audit(){}","audit(int value){}").replace("audit();","audit((int)a);")
                        .replace("a*b;","a*b+0+0+0;").replace("a*c;","a*c+0+0+0;");
                String second=source.substring(source.indexOf("int f("),source.length()-1).replace("int f(","int g(");
                source=source.substring(0,source.length()-1)+second+"}";
            }
            var pack=java.getPackageFragmentRoot(folder).createPackageFragment("",true,monitor);
            var unit=pack.createCompilationUnit("Sample.java",source,true,monitor);
            var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(unit);parser.setResolveBindings(true);
            var ast=(CompilationUnit)parser.createAST(monitor);
            for(var problem:ast.getProblems()) assertFalse(problem.isError(),problem.toString());
            var settings=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
            settings.put(MathCleanUpOptions.CLEANUP,"true");settings.put(MathCleanUpOptions.KINDS,"INT");
            settings.put(MathCleanUpOptions.WORK_BUDGET,"1000000");settings.put(MathCleanUpOptions.MAX_STATES,"20000");
            if(!diagnosticsEnabled) settings.put(MathCleanUpOptions.EXPLANATIONS,"NONE");
            var options=MathCleanUpOptions.parse(settings,17);
            var result=MathematicalAnalysis.analyze(ast,source,options,monitor,-1,0);
            assertEquals(diagnosticsEnabled?2:1,result.evidence().size(),result.diagnostics().toString());
            assertEquals(projectOptions,java.getOptions(true));assertTrue(result.matches(source,projectOptions));
            assertNotNull(result.evidence().getFirst().sourcePlan());
            var environment=MathematicalEnvironment.capture(java,monitor);
            var change=new MathematicalChange("Connected mathematics",unit,result,options,()->options,environment);
            change.initializeValidationData(monitor);
            assertEquals(1,change.getTextEditChangeGroups().length);
            assertFalse(change.isValid(monitor).hasFatalError());
            String preview=change.getPreviewContent(monitor);
            assertTrue(preview.contains(diagnosticsEnabled?"audit((int)a); // unchanged effect":"audit(); // unchanged effect"));
            if(diagnosticsEnabled) {
                assertTrue(preview.contains("// Verified mathematics"));
                var previewParser=ASTParser.newParser(AST.getJLSLatest());previewParser.setProject(java);
                previewParser.setUnitName(unit.getPath().toPortableString());previewParser.setSource(preview.toCharArray());
                previewParser.setResolveBindings(true);previewParser.setCompilerOptions(projectOptions);
                var previewAst=(CompilationUnit)previewParser.createAST(monitor);
                assertEquals(2,Arrays.stream(ast.getProblems()).filter(p->p.getID()==org.eclipse.jdt.core.compiler.IProblem.UnnecessaryCast).count());
                assertEquals(2,Arrays.stream(previewAst.getProblems()).filter(p->p.getID()==org.eclipse.jdt.core.compiler.IProblem.UnnecessaryCast).count());
                assertEquals(Arrays.stream(ast.getProblems()).filter(p->p.isWarning()).count(),
                        Arrays.stream(previewAst.getProblems()).filter(p->p.isWarning()).count());
                for(var problem:previewAst.getProblems())assertFalse(problem.isError(),problem.toString());
            }
            var copied=change.getEdit().copy();
            var undo=change.perform(monitor);assertEquals(preview,unit.getSource());
            assertNotNull(undo);undo.perform(monitor);assertEquals(source,unit.getSource());
            String modified=source.replace("unchanged effect","different effect");
            unit.getBuffer().setContents(modified);unit.save(monitor,true);
            var doc=new Document(modified);
            assertThrows(MalformedTreeException.class,()->copied.apply(doc));
            assertEquals(modified,doc.get(),"Stale validation fails before any edit mutates the document");
        } finally { if(project.exists()) project.delete(true,true,monitor); }
    }
}
