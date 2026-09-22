/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.junit.jupiter.api.Test;

/** A spelling of StandardCharsets is not proof of the referenced field's value. */
class AggregateCharsetBindingTest {
    @Test
    void acceptsImportedJdkConstant() throws Exception {
        assertTrue(compatible("import java.nio.charset.Charset; import java.nio.charset.StandardCharsets;", //$NON-NLS-1$
                "static final Charset ENCODING = StandardCharsets.UTF_8;")); //$NON-NLS-1$
    }

    @Test
    void acceptsQualifiedJdkConstant() throws Exception {
        assertTrue(compatible("import java.nio.charset.Charset;", //$NON-NLS-1$
                "static final Charset ENCODING = java.nio.charset.StandardCharsets.UTF_8;")); //$NON-NLS-1$
    }

    @Test
    void acceptsStaticallyImportedJdkConstant() throws Exception {
        assertTrue(compatible("import java.nio.charset.Charset; import static java.nio.charset.StandardCharsets.UTF_8;", //$NON-NLS-1$
                "static final Charset ENCODING = UTF_8;")); //$NON-NLS-1$
    }

    @Test
    void acceptsParenthesizedJdkConstant() throws Exception {
        assertTrue(compatible("import java.nio.charset.Charset;", //$NON-NLS-1$
                "static final Charset ENCODING = (java.nio.charset.StandardCharsets.UTF_8);")); //$NON-NLS-1$
    }

    @Test
    void acceptsResolvedForName() throws Exception {
        assertTrue(compatible("import java.nio.charset.Charset;", //$NON-NLS-1$
                "static final Charset ENCODING = Charset.forName(\"UTF-8\");")); //$NON-NLS-1$
    }

    @Test
    void rejectsDifferentJdkConstant() throws Exception {
        assertFalse(compatible("import java.nio.charset.Charset;", //$NON-NLS-1$
                "static final Charset ENCODING = java.nio.charset.StandardCharsets.ISO_8859_1;")); //$NON-NLS-1$
    }

    @Test
    void rejectsMutableField() throws Exception {
        assertFalse(compatible("import java.nio.charset.Charset;", //$NON-NLS-1$
                "static Charset ENCODING = java.nio.charset.StandardCharsets.UTF_8;")); //$NON-NLS-1$
    }

    @Test
    void rejectsShadowStandardCharsetsType() throws Exception {
        assertFalse(compatible("import java.nio.charset.Charset;", //$NON-NLS-1$
                """
                static final Charset ENCODING = StandardCharsets.UTF_8;
                static class StandardCharsets {
                    static final Charset UTF_8 = Charset.forName("ISO-8859-1");
                }
                """));
    }

    @Test
    void rejectsShadowQualifiedName() throws Exception {
        assertFalse(compatible("import java.nio.charset.Charset;", //$NON-NLS-1$
                """
                static final Charset ENCODING = java.nio.charset.StandardCharsets.UTF_8;
                static class java { static class nio { static class charset {
                    static class StandardCharsets {
                        static final Charset UTF_8 = Charset.forName("ISO-8859-1");
                    }
                } } }
                """));
    }

    private static boolean compatible(String imports, String members) throws Exception {
        var options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource((imports + "\nclass Subject {\n" + members + "\n}\n").toCharArray()); //$NON-NLS-1$ //$NON-NLS-2$
        parser.setUnitName("Subject.java"); //$NON-NLS-1$
        parser.setEnvironment(new String[0], new String[0], new String[0], true);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(true);
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        assertEquals(0, Arrays.stream(unit.getProblems()).filter(p -> p.isError()).count(),
                Arrays.toString(unit.getProblems()));
        TypeDeclaration type = (TypeDeclaration) unit.types().getFirst();
        FieldDeclaration field = type.getFields()[0];
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) field.fragments().getFirst();
        // Exercise the production reuse decision, not a test-side imitation of it.
        Method decision = ChangeBehavior.class.getDeclaredMethod("isCompatibleCharsetField", //$NON-NLS-1$
                FieldDeclaration.class, VariableDeclarationFragment.class, String.class);
        decision.setAccessible(true);
        return (Boolean) decision.invoke(null, field, fragment, "UTF_8"); //$NON-NLS-1$
    }
}
