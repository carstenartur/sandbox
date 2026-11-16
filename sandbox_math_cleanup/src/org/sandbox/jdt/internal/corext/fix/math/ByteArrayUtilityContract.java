/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import org.eclipse.jdt.core.dom.*;

/** Recognizes the small, source-defined utility contract used to collect a byte prefix. */
final class ByteArrayUtilityContract {
    private static final String EXPECTED = """
            public static byte[] clone(byte[] data) {
                return null == data ? null : data.clone();
            }
            public static byte[] concatenate(byte[] a, byte[] b) {
                if (null == a) { return clone(b); }
                if (null == b) { return clone(a); }
                byte[] r = new byte[a.length + b.length];
                System.arraycopy(a, 0, r, 0, a.length);
                System.arraycopy(b, 0, r, a.length, b.length);
                return r;
            }
            public static byte[] copyOfRange(byte[] original, int from, int to) {
                int newLength = getLength(from, to);
                byte[] copy = new byte[newLength];
                System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
                return copy;
            }
            private static int getLength(int from, int to) {
                int newLength = to - from;
                if (newLength < 0) { throw new IllegalArgumentException(from + " > " + to); }
                return newLength;
            }
            """; //$NON-NLS-1$

    private MethodDeclaration[] templates;

    boolean verify(CompilationUnit request, MethodInvocation cloneCall, MethodInvocation appendCall, MethodInvocation sliceCall) {
        IMethodBinding clone = utility(cloneCall, 1), append = utility(appendCall, 2), slice = utility(sliceCall, 3);
        if (clone == null || append == null || slice == null) return false;
        ITypeBinding owner = clone.getDeclaringClass();
        if (!owner.isFromSource() || owner.getDeclaringClass() != null || !owner.isEqualTo(append.getDeclaringClass())
                || !owner.isEqualTo(slice.getDeclaringClass()) || owner.getSuperclass() == null
                || !"java.lang.Object".equals(owner.getSuperclass().getQualifiedName()) || owner.getInterfaces().length != 0) return false; //$NON-NLS-1$
        if (!bytes(clone.getParameterTypes()[0]) || !bytes(append.getParameterTypes()[0]) || !bytes(append.getParameterTypes()[1])
                || !bytes(slice.getParameterTypes()[0]) || !integer(slice.getParameterTypes()[1]) || !integer(slice.getParameterTypes()[2])) return false;
        var cloneMethod = JavaSourceContext.method(request, clone);
        var appendMethod = JavaSourceContext.method(request, append);
        var sliceMethod = JavaSourceContext.method(request, slice);
        if (cloneMethod == null || appendMethod == null || sliceMethod == null
                || !(cloneMethod.getParent() instanceof TypeDeclaration type) || type.isInterface()
                || type != appendMethod.getParent() || type != sliceMethod.getParent()
                || !JavaSourceContext.chargeSourceWork(request, type.bodyDeclarations().size()) || !inertInitialization(type)) return false;
        var bodies = templates();
        if (!sameBody(cloneMethod, bodies[0]) || !sameBody(appendMethod, bodies[1]) || !sameBody(sliceMethod, bodies[2])) return false;
        // Template shape alone does not establish binding identity. Replay all calls whose behavior the rewrite removes.
        List<MethodInvocation> appendCalls = calls(appendMethod.getBody());
        if (appendCalls.size() != 4 || !sameCall(appendCalls.get(0), clone) || !sameCall(appendCalls.get(1), clone)
                || !libraryCall(appendCalls.get(2), "java.lang.System", "arraycopy") //$NON-NLS-1$ //$NON-NLS-2$
                || !libraryCall(appendCalls.get(3), "java.lang.System", "arraycopy")) return false; //$NON-NLS-1$ //$NON-NLS-2$
        List<MethodInvocation> sliceCalls = calls(sliceMethod.getBody());
        if (sliceCalls.size() != 3 || !libraryCall(sliceCalls.get(1), "java.lang.System", "arraycopy") //$NON-NLS-1$ //$NON-NLS-2$
                || !libraryCall(sliceCalls.get(2), "java.lang.Math", "min")) return false; //$NON-NLS-1$ //$NON-NLS-2$
        IMethodBinding length = sliceCalls.getFirst().resolveMethodBinding();
        if (!ordinaryStatic(length) || !owner.isEqualTo(length.getDeclaringClass()) || !integer(length.getReturnType())
                || length.getParameterTypes().length != 2 || !integer(length.getParameterTypes()[0])
                || !integer(length.getParameterTypes()[1])) return false;
        var lengthMethod = JavaSourceContext.method(request, length);
        if (!sameBody(lengthMethod, bodies[3])) return false;
        var negative = (IfStatement) lengthMethod.getBody().statements().get(1);
        var failure = (ThrowStatement) ((Block) negative.getThenStatement()).statements().getFirst();
        return failure.getExpression().resolveTypeBinding() != null
                && "java.lang.IllegalArgumentException".equals(failure.getExpression().resolveTypeBinding().getQualifiedName()); //$NON-NLS-1$
    }

    private MethodDeclaration[] templates() {
        if (templates == null) {
            var parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
            parser.setSource(EXPECTED.toCharArray());
            templates = ((TypeDeclaration) parser.createAST(null)).getMethods();
        }
        return templates;
    }

    private static boolean inertInitialization(TypeDeclaration type) {
        for (Object member : type.bodyDeclarations()) {
            if (member instanceof Initializer initializer && Modifier.isStatic(initializer.getModifiers())) return false;
            if (member instanceof FieldDeclaration field && Modifier.isStatic(field.getModifiers())) {
                for (Object item : field.fragments()) {
                    var variable = (VariableDeclarationFragment) item;
                    if (variable.getInitializer() != null && (variable.resolveBinding() == null
                            || variable.resolveBinding().getConstantValue() == null)) return false;
                }
            }
        }
        return true;
    }

    private static IMethodBinding utility(MethodInvocation call, int arity) {
        IMethodBinding method = call.resolveMethodBinding();
        return ordinaryStatic(method) && method.getParameterTypes().length == arity && bytes(method.getReturnType())
                && call.getExpression() instanceof Name qualifier && qualifier.resolveBinding() instanceof ITypeBinding type
                && type.isEqualTo(method.getDeclaringClass()) ? method.getMethodDeclaration() : null;
    }

    private static boolean ordinaryStatic(IMethodBinding method) {
        return method != null && !method.isRecovered() && !method.isVarargs() && !method.isGenericMethod()
                && Modifier.isStatic(method.getModifiers()) && !Modifier.isSynchronized(method.getModifiers())
                && !Modifier.isNative(method.getModifiers());
    }

    private static boolean sameBody(MethodDeclaration actual, MethodDeclaration expected) {
        if (actual == null || actual.getBody() == null || actual.parameters().size() != expected.parameters().size()) return false;
        var matcher = new ASTMatcher();
        for (int i = 0; i < actual.parameters().size(); i++) {
            var parameter = (SingleVariableDeclaration) actual.parameters().get(i);
            if (!parameter.getName().subtreeMatch(matcher, ((SingleVariableDeclaration) expected.parameters().get(i)).getName())) return false;
        }
        return actual.getBody().subtreeMatch(matcher, expected.getBody());
    }

    private static List<MethodInvocation> calls(Block body) {
        var result = new java.util.ArrayList<MethodInvocation>();
        body.accept(new ASTVisitor() {
            @Override public boolean visit(MethodInvocation call) { result.add(call); return true; }
        });
        return result;
    }

    private static boolean sameCall(MethodInvocation call, IMethodBinding expected) {
        var actual = call.resolveMethodBinding();
        return actual != null && !actual.isRecovered() && expected.getKey().equals(actual.getMethodDeclaration().getKey());
    }

    static boolean libraryCall(MethodInvocation call, String type, String name) {
        var method = call.resolveMethodBinding();
        return method != null && !method.isRecovered() && Modifier.isStatic(method.getModifiers())
                && !method.getDeclaringClass().isFromSource() && type.equals(method.getDeclaringClass().getQualifiedName())
                && name.equals(method.getName()) && call.getExpression() instanceof Name qualifier
                && qualifier.resolveBinding() instanceof ITypeBinding owner && type.equals(owner.getQualifiedName());
    }

    private static boolean bytes(ITypeBinding type) { return JavaByteArrayPrefixOptimizer.byteArray(type); }
    private static boolean integer(ITypeBinding type) { return JavaByteArrayPrefixOptimizer.primitive(type, "int"); } //$NON-NLS-1$
}
