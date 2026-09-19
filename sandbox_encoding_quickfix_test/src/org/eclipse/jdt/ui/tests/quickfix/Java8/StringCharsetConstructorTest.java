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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/** Verify actual byte-decoding overloads without changing character/copy constructors. */
class StringCharsetConstructorTest {
    @RegisterExtension
    final EclipseJava8 context= new EclipseJava8();

    @BeforeEach
    void setUp() throws Exception {
        JavaCore.setOptions(TestOptions.getDefaultOptions());
        TestOptions.initializeCodeGenerationOptions();
        JavaPlugin.getDefault().getCodeTemplateStore().load();
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void byteArrayConstructorsUseCharset(ChangeBehavior mode) throws Exception {
        ICompilationUnit unit= unit("""
                String decode(byte[] bytes) throws java.io.UnsupportedEncodingException {
                    return new String(bytes) + new String(bytes, "UTF-8")
                            + new String(bytes, 0, bytes.length, "ISO-8859-1");
                }
                """);
        String after= applyAndUndo(unit, mode);
        assertFalse(after.contains("UnsupportedEncodingException"), after); //$NON-NLS-1$
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void nonEncodingConstructorsAreUnchanged(ChangeBehavior mode) throws Exception {
        ICompilationUnit unit= unit("""
                String copy(String text, char[] chars, StringBuilder builder, StringBuffer buffer, int[] points) {
                    return new String() + new String(text) + new String(chars)
                            + new String(builder) + new String(buffer)
                            + new String(chars, 0, chars.length) + new String(points, 0, points.length);
                }
                """);
        assertNull(cleanup(mode).createFix(new CleanUpContext(unit, parse(unit))));
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void nestedByteArrayConstructorRemainsDiscoverable(ChangeBehavior mode) throws Exception {
        ICompilationUnit unit= unit("String copy(byte[] bytes) { return new String(new String(bytes)); }"); //$NON-NLS-1$
        applyAndUndo(unit, mode);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void sameLineReplacementsPreserveUnrelatedNlsTags(ChangeBehavior mode) throws Exception {
        ICompilationUnit unit= unit("""
                String decode(byte[] bytes) throws java.io.UnsupportedEncodingException {
                    return "label" + new String(bytes, "UTF-8") + new String(bytes, "ISO-8859-1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ keep this comment
                }
                """);
        String after= applyAndUndo(unit, mode);
        assertTrue(after.contains("$NON-NLS-1$"), after); //$NON-NLS-1$
        assertFalse(after.contains("$NON-NLS-2$"), after); //$NON-NLS-1$
        assertFalse(after.contains("$NON-NLS-3$"), after); //$NON-NLS-1$
        assertTrue(after.contains("keep this comment"), after); //$NON-NLS-1$
    }

    private String applyAndUndo(ICompilationUnit unit, ChangeBehavior mode) throws Exception {
        String before= unit.getSource();
        List<ClassInstanceCreation> original= constructors(parse(unit));
        var cleanup= cleanup(mode);
        var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
        assertNotNull(fix, "Byte-decoding constructor must be modernized"); //$NON-NLS-1$
        var change= fix.createChange(null);
        try {
            String preview= change.getPreviewContent(null);
            assertEquals(preview, change.getPreviewContent(null));
            assertEquals(before, unit.getSource());
            change.initializeValidationData(null);
            var undo= change.perform(null);
            assertNotNull(undo);
            try {
                List<ClassInstanceCreation> updated= constructors(parse(unit));
                assertEquals(original.size(), updated.size());
                for (int index= 0; index < original.size(); index++) {
                    var parameters= original.get(index).resolveConstructorBinding().getParameterTypes();
                    var replacement= updated.get(index).resolveConstructorBinding().getParameterTypes();
                    if (parameters.length > 0 && "byte[]".equals(parameters[0].getQualifiedName())) { //$NON-NLS-1$
                        assertEquals("java.nio.charset.Charset", replacement[replacement.length - 1].getQualifiedName(), preview); //$NON-NLS-1$
                    } else {
                        assertEquals(Arrays.stream(parameters).map(type -> type.getQualifiedName()).toList(),
                                Arrays.stream(replacement).map(type -> type.getQualifiedName()).toList(), preview);
                    }
                }
                assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))), "Idempotence"); //$NON-NLS-1$
                var redo= undo.perform(null);
                if (redo != null) redo.dispose();
                assertEquals(before, unit.getSource());
                parse(unit);
                return preview;
            } finally { undo.dispose(); }
        } finally { change.dispose(); }
    }

    private ICompilationUnit unit(String members) throws Exception {
        return context.getSourceFolder().createPackageFragment("test1", false, null) //$NON-NLS-1$
                .createCompilationUnit("E1.java", "package test1;\npublic class E1 {\n" + members + "}\n", false, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static CompilationUnit parse(ICompilationUnit unit) throws Exception {
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        CompilationUnit root= (CompilationUnit) parser.createAST(null);
        assertEquals(List.of(), Arrays.stream(root.getProblems()).filter(IProblem::isError).map(IProblem::getMessage).toList(), unit.getSource());
        return root;
    }

    private static List<ClassInstanceCreation> constructors(CompilationUnit root) {
        List<ClassInstanceCreation> result= new ArrayList<>();
        root.accept(new ASTVisitor() {
            @Override public boolean visit(ClassInstanceCreation node) {
                assertNotNull(node.resolveConstructorBinding());
                assertFalse(node.resolveConstructorBinding().isRecovered());
                result.add(node);
                return true;
            }
        });
        return result;
    }

    private static UseExplicitEncodingCleanUpCore cleanup(ChangeBehavior mode) {
        return new UseExplicitEncodingCleanUpCore(Map.of(MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", switch (mode) { //$NON-NLS-1$
            case KEEP_BEHAVIOR -> MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR;
            case ENFORCE_UTF8 -> MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
            case ENFORCE_UTF8_AGGREGATE -> MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8;
        }, "true")); //$NON-NLS-1$
    }
}
