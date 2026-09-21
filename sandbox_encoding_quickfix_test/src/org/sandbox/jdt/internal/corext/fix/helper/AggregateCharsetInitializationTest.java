/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.junit.jupiter.api.Test;

/** Availability, not just the final value, determines whether a field is reusable. */
class AggregateCharsetInitializationTest {
    @Test
    void permitsOrdinaryFieldReuse() throws Exception {
        assertTrue(safe("""
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                static String value() { return Charset.forName("UTF-8").name(); }
                """));
    }

    @Test
    void permitsReuseAfterCompileTimeConstants() throws Exception {
        assertTrue(safe("""
                static final int VERSION = 1 + 2;
                static final String LABEL = "UTF" + "-8";
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                static String value() { return Charset.forName("UTF-8").name(); }
                """));
    }

    @Test
    void rejectsInstanceMethodCalledByEarlierStaticInitializer() throws Exception {
        assertFalse(safe("""
                static final String INITIAL = new Subject().value();
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                String value() { return Charset.forName("UTF-8").name(); }
                """));
    }

    @Test
    void rejectsConstructorCalledByEarlierStaticInitializer() throws Exception {
        assertFalse(safe("""
                static final Subject INITIAL = new Subject();
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                final String value;
                Subject() { value = Charset.forName("UTF-8").name(); }
                """));
    }

    @Test
    void rejectsInstanceFieldInitializedByEarlierStaticConstruction() throws Exception {
        assertFalse(safe("""
                static final Subject INITIAL = new Subject();
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                final String value = Charset.forName("UTF-8").name();
                """));
    }

    @Test
    void rejectsInstanceInitializerExecutedByEarlierStaticConstruction() throws Exception {
        assertFalse(safe("""
                static final Subject INITIAL = new Subject();
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                final String value;
                { value = Charset.forName("UTF-8").name(); }
                """));
    }

    @Test
    void rejectsMethodReferenceInvokedBeforeFieldInitialization() throws Exception {
        assertFalse(safe("""
                static final java.util.function.Supplier<String> GET = Subject::value;
                static final String INITIAL = GET.get();
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                static String value() { return Charset.forName("UTF-8").name(); }
                """));
    }

    @Test
    void rejectsCallbackThroughAnotherDeclaringType() throws Exception {
        assertFalse(safe("""
                static final String INITIAL = Relay.call(Subject::value);
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                static String value() { return Charset.forName("UTF-8").name(); }
                static class Relay {
                    static String call(java.util.function.Supplier<String> supplier) {
                        return supplier.get();
                    }
                }
                """));
    }

    @Test
    void rejectsMethodCalledByEarlierFragmentOfSameDeclaration() throws Exception {
        assertFalse(safe("""
                static final Charset INITIAL = value(), UTF_8 = StandardCharsets.UTF_8;
                static Charset value() { return Charset.forName("UTF-8"); }
                """));
    }

    @Test
    void rejectsSuperclassInitializationCallback() throws Exception {
        assertFalse(safe("class Parent { static final String INITIAL = Subject.value(); }", //$NON-NLS-1$
                " extends Parent", //$NON-NLS-1$
                """
                static final Charset UTF_8 = StandardCharsets.UTF_8;
                static String value() { return Charset.forName("UTF-8").name(); }
                """));
    }

    private static boolean safe(String members) throws Exception {
        return safe("", "", members); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean safe(String otherTypes, String superclass, String members) throws Exception {
        var options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(("import java.nio.charset.Charset; import java.nio.charset.StandardCharsets;\n" //$NON-NLS-1$
                + otherTypes + "\npublic class Subject" + superclass + " {\n" + members + "\n}\n").toCharArray()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        parser.setUnitName("Subject.java"); //$NON-NLS-1$
        parser.setEnvironment(new String[0], new String[0], new String[0], true);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(true);
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        assertEquals(0, Arrays.stream(unit.getProblems()).filter(problem -> problem.isError()).count(),
                Arrays.toString(unit.getProblems()));
        TypeDeclaration owner = (TypeDeclaration) unit.types().getLast();
        VariableDeclarationFragment target = null;
        for (var field : owner.getFields()) {
            for (Object candidate : field.fragments()) {
                if (candidate instanceof VariableDeclarationFragment fragment
                        && "UTF_8".equals(fragment.getName().getIdentifier())) { //$NON-NLS-1$
                    target = fragment;
                }
            }
        }
        assertNotNull(target, "fixture must contain the reused field"); //$NON-NLS-1$
        var uses = new ArrayList<MethodInvocation>();
        owner.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation invocation) {
                var binding = invocation.resolveMethodBinding();
                if (binding != null && !binding.isRecovered()
                        && "forName".equals(binding.getName()) //$NON-NLS-1$
                        && "java.nio.charset.Charset".equals(binding.getDeclaringClass().getQualifiedName())) { //$NON-NLS-1$
                    uses.add(invocation);
                }
                return true;
            }
        });
        assertEquals(1, uses.size(), "fixture must contain exactly one bound modernization candidate"); //$NON-NLS-1$
        // Invoke the production decision; do not duplicate its reachability logic in the test.
        Method decision = ChangeBehavior.class.getDeclaredMethod("isSafeToReadField", //$NON-NLS-1$
                TypeDeclaration.class, VariableDeclarationFragment.class, ASTNode.class);
        decision.setAccessible(true);
        return (Boolean) decision.invoke(null, owner, target, uses.getFirst());
    }
}
