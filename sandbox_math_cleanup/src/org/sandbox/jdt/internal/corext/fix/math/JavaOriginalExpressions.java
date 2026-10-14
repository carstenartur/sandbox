/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;

/** Source rendering only. The resulting emission must pass the normal independent Java verifier. */
final class JavaOriginalExpressions {
    private JavaOriginalExpressions() { }

    static JavaComputationEmitter.Emission restore(CompilationUnit unit, String source, JavaComputationRegion region,
            JavaComputationEmitter.Emission emission) {
        Map<BigInteger, Expression> literals = new HashMap<>();
        ASTNode scope = NodeFinder.perform(unit, region.start(), region.length());
        if (scope == null) return emission;
        scope.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                return node.getStartPosition() < (long)region.start()+region.length()
                    && (long)node.getStartPosition()+node.getLength() > region.start();
            }
            @Override public boolean visit(MethodInvocation call) {
                var binding = call.resolveMethodBinding();
                if (binding != null && !binding.isRecovered() && Modifier.isStatic(binding.getModifiers())
                        && "java.math.BigInteger".equals(binding.getDeclaringClass().getQualifiedName()) //$NON-NLS-1$
                        && "valueOf".equals(binding.getName()) && call.arguments().size() == 1 //$NON-NLS-1$
                        && call.getExpression() instanceof Name name && name.resolveBinding() instanceof ITypeBinding) {
                    Expression argument = (Expression)call.arguments().getFirst();
                    if (!(argument instanceof NumberLiteral || argument instanceof PrefixExpression prefix && prefix.getOperand() instanceof NumberLiteral)) return true;
                    Object value = argument.resolveConstantExpressionValue();
                    if (value instanceof Long || value instanceof Integer)
                        literals.putIfAbsent(BigInteger.valueOf(((Number)value).longValue()), call);
                }
                return true;
            }
        });
        var outputs = new HashMap<String,String>();
        for (var output : region.outputs()) {
            String generated = emission.outputValues().get(output.id());
            if (generated == null || generated.length() > 100_000) throw new IllegalArgumentException("SOURCE_RENDERING_BOUND"); //$NON-NLS-1$
            var parser = ASTParser.newParser(unit.getAST().apiLevel());
            parser.setKind(ASTParser.K_EXPRESSION); parser.setSource(generated.toCharArray());
            ASTNode tree = parser.createAST(null);
            if (!(tree instanceof Expression expression) || (tree.getFlags() & ASTNode.MALFORMED) != 0)
                throw new IllegalArgumentException("GENERATED_EXPRESSION_UNPARSED"); //$NON-NLS-1$
            Expression normalized = (Expression)normalize(expression, literals, 0);
            ASTNode original = NodeFinder.perform(unit, output.initializerStart(), output.initializerLength());
            String rendered = original instanceof Expression old ? render(unit, source, old, normalized, 0) : normalized.toString();
            outputs.put(output.id(), rendered);
        }
        return new JavaComputationEmitter.Emission(emission.statements(), outputs);
    }
    @SuppressWarnings("unchecked")
    private static ASTNode normalize(ASTNode input, Map<BigInteger, Expression> literals, int depth) {
        if (depth > 128) throw new IllegalArgumentException("SOURCE_RENDERING_BOUND"); //$NON-NLS-1$
        AST ast = input.getAST();
        if (input instanceof ClassInstanceCreation creation && creation.getAnonymousClassDeclaration() == null
                && "java.math.BigInteger".equals(creation.getType().toString()) && creation.arguments().size() == 1 //$NON-NLS-1$
                && creation.arguments().getFirst() instanceof StringLiteral literal) {
            try {
                Expression original = literals.get(new BigInteger(literal.getLiteralValue()));
                if (original != null) return ASTNode.copySubtree(ast, original);
            } catch (NumberFormatException unsupported) { /* Keep the original emitted node. */ }
        }
        ASTNode copy = ASTNode.copySubtree(ast, input);
        for (Object property : input.structuralPropertiesForType()) {
            StructuralPropertyDescriptor descriptor = (StructuralPropertyDescriptor)property;
            if (descriptor instanceof ChildPropertyDescriptor) {
                var child = (ASTNode)input.getStructuralProperty(descriptor);
                if (child != null) copy.setStructuralProperty(descriptor, normalize(child, literals, depth+1));
            } else if (descriptor instanceof ChildListPropertyDescriptor) {
                var original = (java.util.List<ASTNode>)input.getStructuralProperty(descriptor);
                var children = (java.util.List<ASTNode>)copy.getStructuralProperty(descriptor); children.clear();
                for (var child : original) children.add(normalize(child,literals,depth+1));
            }
        }
        if (copy instanceof ParenthesizedExpression parentheses) {
            Expression inner = parentheses.getExpression();
            if (inner instanceof MethodInvocation || inner instanceof Name || inner instanceof ClassInstanceCreation
                    || inner instanceof NumberLiteral || inner instanceof StringLiteral)
                return ASTNode.copySubtree(ast, inner);
        }
        return copy;
    }
    private static String render(CompilationUnit unit, String source, Expression original, Expression target, int depth) {
        if (depth > 128) throw new IllegalArgumentException("SOURCE_RENDERING_BOUND"); //$NON-NLS-1$
        if (original.subtreeMatch(new ASTMatcher(), target)) return text(source, original);
        // Preserve unchanged method syntax and whitespace. Removed operations are dictated by the verified target.
        if (target instanceof MethodInvocation after) {
            Expression cursor = original;
            for (int steps=0; steps<64 && cursor instanceof MethodInvocation before; steps++) {
                if (before.getName().getIdentifier().equals(after.getName().getIdentifier())
                        && before.arguments().size() == after.arguments().size()
                        && before.typeArguments().isEmpty() && after.typeArguments().isEmpty()
                        && before.getExpression() != null && after.getExpression() != null) {
                    requireNoLostComments(unit, original, before);
                    var changes = new java.util.ArrayList<Piece>();
                    changes.add(new Piece(before.getExpression().getStartPosition(), before.getExpression().getLength(),
                        render(unit,source,before.getExpression(),after.getExpression(),depth+1)));
                    for (int i=0;i<before.arguments().size();i++) {
                        Expression a=(Expression)before.arguments().get(i), b=(Expression)after.arguments().get(i);
                        changes.add(new Piece(a.getStartPosition(),a.getLength(),render(unit,source,a,b,depth+1)));
                    }
                    StringBuilder result = new StringBuilder(text(source,before));
                    changes.sort(java.util.Comparator.comparingInt(Piece::start).reversed());
                    for (var change : changes) {
                        int start=change.start()-before.getStartPosition();
                        result.replace(start,start+change.length(),change.replacement());
                    }
                    return result.toString();
                }
                cursor=before.getExpression();
            }
        }
        requireNoLostComments(unit, original, null);
        return target.toString();
    }
    private static void requireNoLostComments(CompilationUnit unit, ASTNode outer, ASTNode retained) {
        int start=outer.getStartPosition(), end=start+outer.getLength();
        int keep=retained == null ? -1 : retained.getStartPosition(), keepEnd=retained == null ? -1 : keep+retained.getLength();
        for (Object value : unit.getCommentList()) {
            ASTNode comment=(ASTNode)value; int position=comment.getStartPosition();
            if (position >= start && position < end && !(position >= keep && position < keepEnd))
                throw new IllegalArgumentException("SOURCE_COMMENT_WOULD_BE_LOST"); //$NON-NLS-1$
        }
    }
    private record Piece(int start,int length,String replacement) { }
    private static String text(String source, ASTNode node) {
        return source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength());
    }
}
