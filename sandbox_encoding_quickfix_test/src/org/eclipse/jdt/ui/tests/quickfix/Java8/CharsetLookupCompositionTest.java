/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/** Replacements sharing a statement must compose in both entry points. */
class CharsetLookupCompositionTest {
    @RegisterExtension
    final EclipseJava8 context= new EclipseJava8();

    @BeforeEach
    void setUp() throws Exception {
        JavaCore.setOptions(TestOptions.getDefaultOptions());
        TestOptions.initializeCodeGenerationOptions();
        JavaPlugin.getDefault().getCodeTemplateStore().load();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameLineLookupsKeepTheSurvivingTagAndComment(boolean standaloneDsl) throws Exception {
        String before= """
                package test1;

                import java.nio.charset.Charset;

                public class E1 {
                    Charset[] values(String requested) {
                        return new Charset[] {Charset.forName("UTF-8"), Charset.forName("ISO-8859-1"), Charset.forName("windows-1252"), Charset.forName(requested)}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ preserve this comment
                    }
                }
                """;
        ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test1", false, null) //$NON-NLS-1$
                .createCompilationUnit("E1.java", before, false, null); //$NON-NLS-1$
        var change= change(unit, standaloneDsl);
        assertNotNull(change);
        try {
            String after= change.getPreviewContent(null);
            assertEquals(before, unit.getSource(), "Preview must not modify the source"); //$NON-NLS-1$
            assertEquals(after, change.getPreviewContent(null));
            assertTrue(after.contains("import java.nio.charset.StandardCharsets;"), after); //$NON-NLS-1$
            assertTrue(after.contains("new Charset[] {StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1,"), after); //$NON-NLS-1$
            assertTrue(after.contains("Charset.forName(\"windows-1252\"), Charset.forName(requested)"), after); //$NON-NLS-1$
            assertTrue(after.contains("//$NON-NLS-1$ preserve this comment"), after); //$NON-NLS-1$
            assertFalse(after.contains("$NON-NLS-2$"), after); //$NON-NLS-1$
            assertFalse(after.contains("$NON-NLS-3$"), after); //$NON-NLS-1$
            change.initializeValidationData(null);
            var undo= change.perform(null);
            assertNotNull(undo);
            try {
                assertEquals(after, unit.getSource());
                parse(unit);
                assertNull(change(unit, standaloneDsl), "The complete rewrite must be idempotent"); //$NON-NLS-1$
                var redo= undo.perform(null);
                if (redo != null) redo.dispose();
                assertEquals(before, unit.getSource(), "Undo must restore every byte"); //$NON-NLS-1$
                parse(unit);
            } finally { undo.dispose(); }
        } finally { change.dispose(); }
    }

    private static CompilationUnitChange change(ICompilationUnit unit, boolean standaloneDsl) throws Exception {
        CompilationUnit root= parse(unit);
        if (standaloneDsl) {
            var operations= new LinkedHashSet<CompilationUnitRewriteOperation>();
            HintFileFixCore.findOperationsForBundle(root, "encoding", operations, new HashSet<>(), //$NON-NLS-1$
                    Map.of(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8, "sandbox.cleanup.mode", "KEEP_BEHAVIOR")); //$NON-NLS-1$ //$NON-NLS-2$
            return operations.isEmpty() ? null : new NlsAwareCleanUpFix("Charset lookups", root, //$NON-NLS-1$
                    operations.toArray(CompilationUnitRewriteOperation[]::new)).createChange(null);
        }
        var cleanup= new UseExplicitEncodingCleanUpCore(Map.of(
                MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", //$NON-NLS-1$
                MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR, "true")); //$NON-NLS-1$
        var fix= cleanup.createFix(new CleanUpContext(unit, root));
        return fix == null ? null : fix.createChange(null);
    }

    private static CompilationUnit parse(ICompilationUnit unit) throws Exception {
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        CompilationUnit root= (CompilationUnit) parser.createAST(null);
        assertEquals(List.of(), Arrays.stream(root.getProblems()).filter(IProblem::isError)
                .map(IProblem::getMessage).toList(), unit.getSource());
        return root;
    }
}
