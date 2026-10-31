/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.AbstractExceptionAnalyzer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.ElementDescriptor;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.transformer.LoopModelTransformer;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Handler for converting traditional index-based for-loops to IntStream.range() operations.
 * 
 * <p>This handler converts classic for-loops with integer counters to functional IntStream operations:</p>
 * <pre>{@code
 * // Before:
 * for (int i = 0; i < 10; i++) {
 *     System.out.println(i);
 * }
 * 
 * // After:
 * IntStream.range(0, 10).forEach(i -> System.out.println(i));
 * }</pre>
 * 
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 *   <li>Initializer: {@code int i = start} (single variable declaration with int type)</li>
 *   <li>Condition: {@code i < end} or {@code i <= end} (comparison with loop variable)</li>
 *   <li>Updater: {@code i++} or {@code ++i} (increment operator)</li>
 *   <li>Upper bound: an invariant primitive integral expression; inclusive bounds must
 *       exclude {@link Integer#MAX_VALUE} so the final update cannot wrap</li>
 * </ul>
 * 
 * <p><b>Infrastructure:</b></p>
 * <ul>
 *   <li>Uses {@link SourceDescriptor.SourceType#EXPLICIT_RANGE} for range representation</li>
 *   <li>Leverages existing {@link ASTStreamRenderer} for IntStream.range() rendering</li>
 *   <li>Builds {@link LoopModel} with {@link ForEachTerminal} for body statements</li>
 * </ul>
 * 
 * <p><b>Thread-Safety Guards:</b></p>
 * <ul>
 *   <li><b>Nested loop guard</b>: {@code isNestedInsideLoop()} rejects loops inside other loops
 *       to avoid interfering with the {@code EnhancedForHandler}'s scope analysis and to
 *       prevent generating IntStream calls inside lambdas where mutable loop state could be captured</li>
 *   <li><b>Unconvertible statement detection</b>: {@code containsUnconvertibleStatements()} rejects
 *       loops with {@code break}, {@code continue}, or {@code return} which cannot be expressed
 *       in lambda bodies</li>
 *   <li><b>Sequential-only streams</b>: Always generates {@code IntStream.range()} (sequential),
 *       never parallel streams, avoiding complex synchronization requirements</li>
 *   <li><b>Header and capture checks</b>: Bounds cannot depend on calls, mutable heap state,
 *       or the counter; the body cannot write the counter or capture mutable locals</li>
 * </ul>
 * 
 * <p><b>Naming Note:</b> This class is named after the <i>source</i> loop type (traditional for-loop),
 * not the target format. The architecture supports bidirectional transformations, so the name
 * describes what loop pattern this handler processes.</p>
 * 
 * @see SourceDescriptor.SourceType#EXPLICIT_RANGE
 * @see ASTStreamRenderer#renderSource(SourceDescriptor)
 */
public class TraditionalForHandler extends AbstractFunctionalCall<ForStatement> {
    
    @Override
    public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
                     Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
        
        LoopConversionService.scanRoot(compilationUnit).accept(new ASTVisitor() {
            @Override
            public boolean visit(ForStatement node) {
                if (nodesprocessed.contains(node)) {
                    return false;
                }
                
                // Analyze the for-loop structure
                ForLoopPattern pattern = analyzeForLoop(node);
                if (pattern == null) {
                    return true; // Not a convertible pattern, continue visiting
                }
                
                // Mark as processed and add operation
                nodesprocessed.add(node);
                ReferenceHolder<ASTNode, Object> holder = ReferenceHolder.create();
                holder.put(node, pattern);
                operations.add(fixcore.rewrite(node, holder));
                
                return false; // Don't visit children of convertible loops
            }
        });
    }
    
    /**
     * Analyzes a ForStatement to determine if it follows the convertible pattern.
     * 
     * @param forStmt the ForStatement to analyze
     * @return a ForLoopPattern if convertible, null otherwise
     */
    private ForLoopPattern analyzeForLoop(ForStatement forStmt) {
        // Skip for-loops nested inside other loops to avoid converting
        // inner loops that the EnhancedForHandler expects to remain unchanged
        if (isNestedInsideLoop(forStmt)) {
            return null;
        }
        
        // Check initializer: must be single variable declaration of type int
        List<?> initializers = forStmt.initializers();
        if (initializers.size() != 1) {
            return null;
        }
        
        Object initObj = initializers.get(0);
        if (!(initObj instanceof VariableDeclarationExpression)) {
            return null;
        }
        
        VariableDeclarationExpression varDecl = (VariableDeclarationExpression) initObj;
        Type type = varDecl.getType();
        if (!isPrimitiveInt(type)) {
            return null;
        }
        
        List<?> fragments = varDecl.fragments();
        if (fragments.size() != 1) {
            return null;
        }
        
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragments.get(0);
        String loopVarName = fragment.getName().getIdentifier();
        Expression startExpr = fragment.getInitializer();
        
        if (startExpr == null) {
            return null;
        }

        IVariableBinding counter = fragment.resolveBinding();
        if (counter == null || counter.isRecovered()) {
            return null;
        }
        
        // Check condition: must be infix comparison with loop variable
        Expression condition = forStmt.getExpression();
        if (!(condition instanceof InfixExpression)) {
            return null;
        }
        
        InfixExpression infixCond = (InfixExpression) condition;
        InfixExpression.Operator operator = infixCond.getOperator();
        
        // Check that left operand is the loop variable
        Expression leftOperand = infixCond.getLeftOperand();
        if (!(leftOperand instanceof SimpleName)) {
            return null;
        }
        
        SimpleName leftName = (SimpleName) leftOperand;
        if (!leftName.getIdentifier().equals(loopVarName)) {
            return null;
        }
        
        Expression endExpr = infixCond.getRightOperand();
        if (endExpr == null) {
            return null;
        }
        
        boolean inclusive = false;
        if (operator == InfixExpression.Operator.LESS) {
            // i < end  →  IntStream.range(start, end)
            inclusive = false;
        } else if (operator == InfixExpression.Operator.LESS_EQUALS) {
            // i <= end  →  IntStream.rangeClosed(start, end) or range(start, end+1)
            inclusive = true;
        } else {
            return null; // Unsupported operator
        }

        // A range snapshots its end once, whereas a Java loop evaluates the
        // condition before every iteration, including the final failed check.
        if (!isStableIntegralBound(endExpr) || (inclusive && !canIncrementPast(endExpr))) {
            return null;
        }
        
        // Check updater: must be i++ or ++i
        List<?> updaters = forStmt.updaters();
        if (updaters.size() != 1) {
            return null;
        }
        
        Object updaterObj = updaters.get(0);
        boolean isIncrement = false;
        
        if (updaterObj instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression) updaterObj;
            if (postfix.getOperator() == PostfixExpression.Operator.INCREMENT) {
                Expression operand = postfix.getOperand();
                if (operand instanceof SimpleName && ((SimpleName) operand).getIdentifier().equals(loopVarName)) {
                    isIncrement = true;
                }
            }
        } else if (updaterObj instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression) updaterObj;
            if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT) {
                Expression operand = prefix.getOperand();
                if (operand instanceof SimpleName && ((SimpleName) operand).getIdentifier().equals(loopVarName)) {
                    isIncrement = true;
                }
            }
        }
        
        if (!isIncrement) {
            return null;
        }
        
        // Extract body
        Statement body = forStmt.getBody();
        
        // Check if body contains unconvertible statements (break, continue, return)
        if (containsUnconvertibleStatements(body)) {
            return null;
        }

        if (hasUnsafeCapturesOrCounterWrites(body, counter)) {
            return null;
        }

        if (CheckedBodyExceptions.escape(body)) {
            return null;
        }

        // Header comments have no unambiguous counterpart after the counter
        // declaration/condition/update disappear. Keep their original location.
        if (containsComments(forStmt, forStmt.getStartPosition(), body.getStartPosition())) {
            return null;
        }
        
        // Issue #670: Check if the index variable is used in the body for complex
        // patterns like a[i+1], a[i-1], or i%2 that indicate neighbor access or
        // non-trivial index semantics. These patterns suggest the loop relies on
        // index arithmetic that may not be safely convertible.
        if (usesIndexBeyondSimpleAccess(body, loopVarName)) {
            return null;
        }

        // Admission uses a disposable rewrite so an unavailable qualifier cannot
        // advertise a quick assist or leave an import behind on an unchanged loop.
        CompilationUnit unit = (CompilationUnit) forStmt.getRoot();
        if (!(unit.getTypeRoot() instanceof ICompilationUnit)
                || streamTypeName(forStmt, ImportRewrite.create(unit, true)) == null) {
            return null;
        }
        
        return new ForLoopPattern(loopVarName, startExpr, endExpr, inclusive, body);
    }

    private String streamTypeName(ForStatement loop, ImportRewrite imports) {
        var context = new ContextSensitiveImportRewriteContext(loop, imports);
        String name = imports.addImport("java.util.stream.IntStream", context); //$NON-NLS-1$
        // A qualified type used as an expression is still obscured by a visible
        // variable or type called java. No safe static receiver is available then.
        if (name.indexOf('.') >= 0 && context.findInContext("", "java", ImportRewriteContext.KIND_TYPE) //$NON-NLS-1$ //$NON-NLS-2$
                != ImportRewriteContext.RES_NAME_UNKNOWN) {
            return null;
        }
        return name;
    }

    /** Repeated reads must have the same value and the same first possible exception. */
    private boolean isStableIntegralBound(Expression expression) {
        ITypeBinding type = expression.resolveTypeBinding();
        if (type == null || !type.isPrimitive()
                || !Set.of("byte", "short", "char", "int").contains(type.getName())) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            return false;
        }
        if (expression instanceof ParenthesizedExpression parenthesized) {
            return isStableIntegralBound(parenthesized.getExpression());
        }
        if (expression instanceof NumberLiteral || expression instanceof CharacterLiteral) {
            return true;
        }
        if (expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding variable) {
            return !variable.isRecovered() && (variable.getConstantValue() != null || isStableLocal(variable));
        }
        if (expression instanceof QualifiedName name) {
            if (name.getName().getIdentifier().equals("length") && isStableArray(name.getQualifier())) { //$NON-NLS-1$
                return true;
            }
            return name.getQualifier().resolveBinding() instanceof ITypeBinding
                    && name.resolveBinding() instanceof IVariableBinding variable
                    && !variable.isRecovered() && variable.getConstantValue() != null;
        }
        if (expression instanceof FieldAccess field) {
            return field.getName().getIdentifier().equals("length") && isStableArray(field.getExpression()); //$NON-NLS-1$
        }
        if (expression instanceof PrefixExpression prefix) {
            return (prefix.getOperator() == PrefixExpression.Operator.PLUS
                    || prefix.getOperator() == PrefixExpression.Operator.MINUS
                    || prefix.getOperator() == PrefixExpression.Operator.COMPLEMENT)
                    && isStableIntegralBound(prefix.getOperand());
        }
        if (expression instanceof CastExpression cast) {
            return isStableIntegralBound(cast.getExpression());
        }
        if (expression instanceof InfixExpression infix) {
            if (!isStableIntegralBound(infix.getLeftOperand()) || !isStableIntegralBound(infix.getRightOperand())) {
                return false;
            }
            for (Object operand : infix.extendedOperands()) {
                if (!isStableIntegralBound((Expression) operand)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isStableLocal(IVariableBinding variable) {
        return !variable.isField() && !variable.isRecovered()
                && (Modifier.isFinal(variable.getModifiers()) || variable.isEffectivelyFinal());
    }

    private boolean isStableArray(Expression expression) {
        while (expression instanceof ParenthesizedExpression parenthesized) {
            expression = parenthesized.getExpression();
        }
        return expression instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding variable
                && variable.getType().isArray() && isStableLocal(variable);
    }

    /** rangeClosed alone would still truncate a source loop that wraps at MAX_VALUE. */
    private boolean canIncrementPast(Expression end) {
        Object constant = end.resolveConstantExpressionValue();
        if (constant instanceof Number number) {
            return number.intValue() < Integer.MAX_VALUE;
        }
        if (constant instanceof Character) {
            return true;
        }
        return !end.resolveTypeBinding().getName().equals("int"); //$NON-NLS-1$
    }

    private boolean hasUnsafeCapturesOrCounterWrites(Statement body, IVariableBinding counter) {
        boolean[] unsafe = { false };
        CompilationUnit root = (CompilationUnit) body.getRoot();
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (node.isDeclaration() || !(node.resolveBinding() instanceof IVariableBinding variable)
                        || variable.isField()) {
                    return true;
                }
                if (variable.isEqualTo(counter)) {
                    ASTNode operand = node;
                    while (operand.getParent() instanceof ParenthesizedExpression) {
                        operand = operand.getParent();
                    }
                    ASTNode parent = operand.getParent();
                    if (parent instanceof Assignment assignment && assignment.getLeftHandSide() == operand
                            || parent instanceof PostfixExpression
                            || parent instanceof PrefixExpression prefix
                                    && (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
                                            || prefix.getOperator() == PrefixExpression.Operator.DECREMENT)) {
                        unsafe[0] = true;
                    }
                } else if (!isStableLocal(variable)) {
                    ASTNode declaration = root.findDeclaringNode(variable);
                    if (declaration == null || declaration.getStartPosition() < body.getStartPosition()
                            || declaration.getStartPosition() >= body.getStartPosition() + body.getLength()) {
                        unsafe[0] = true;
                    }
                }
                return true;
            }
        });
        return unsafe[0];
    }

    private boolean containsComments(ASTNode owner, int start, int end) {
        for (Object entry : ((CompilationUnit) owner.getRoot()).getCommentList()) {
            Comment comment = (Comment) entry;
            if (comment.getStartPosition() >= start && comment.getStartPosition() < end) {
                return true;
            }
        }
        return false;
    }

    /** IntConsumer cannot propagate checked exceptions, even when the enclosing method can. */
    private static final class CheckedBodyExceptions extends AbstractExceptionAnalyzer {
        private boolean uncertain;

        static boolean escape(Statement body) {
            CheckedBodyExceptions analyzer = new CheckedBodyExceptions();
            body.accept(analyzer);
            return analyzer.uncertain || analyzer.getCurrentExceptions().stream().anyMatch(exception ->
                    !Bindings.isRuntimeException(exception)
                            && Bindings.findTypeInHierarchy(exception, "java.lang.Error") == null); //$NON-NLS-1$
        }

        @Override
        public boolean preVisit2(ASTNode node) {
            if (node instanceof Expression resource && node.getParent() instanceof TryStatement statement
                    && statement.resources().contains(node)) {
                ITypeBinding type = resource.resolveTypeBinding();
                check(type == null || type.isRecovered() ? null
                        : Bindings.findMethodInHierarchy(type, "close", new ITypeBinding[0]), node); //$NON-NLS-1$
            }
            return true;
        }

        // The resource check above includes declarations, var and Java 9 resource references.
        @Override
        public boolean visit(VariableDeclarationExpression node) { return true; }

        @Override
        public boolean visit(RecordDeclaration node) { return false; }

        @Override
        public boolean visit(MethodInvocation node) { return check(node.resolveMethodBinding(), node); }

        @Override
        public boolean visit(SuperMethodInvocation node) { return check(node.resolveMethodBinding(), node); }

        @Override
        public boolean visit(ClassInstanceCreation node) { return check(node.resolveConstructorBinding(), node); }

        @Override
        public boolean visit(ThrowStatement node) {
            ITypeBinding type = node.getExpression().resolveTypeBinding();
            if (type == null || type.isRecovered()) uncertain = true;
            else addException(type, node.getAST());
            return true;
        }

        private boolean check(IMethodBinding binding, ASTNode node) {
            if (binding == null || binding.isRecovered()) {
                uncertain = true;
            } else {
                for (ITypeBinding exception : binding.getExceptionTypes()) {
                    if (exception.isRecovered()) uncertain = true;
                    else addException(exception, node.getAST());
                }
            }
            return true;
        }
    }
    
    /**
     * Checks if a Type represents primitive int.
     */
    private boolean isPrimitiveInt(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primType = (PrimitiveType) type;
            return primType.getPrimitiveTypeCode() == PrimitiveType.INT;
        }
        return false;
    }
    
    /**
     * Checks if the for-loop is nested inside another loop (enhanced-for, while, for, do-while).
     * Nested traditional for-loops are skipped to avoid interfering with the EnhancedForHandler's
     * analysis of outer enhanced-for loops.
     */
    private boolean isNestedInsideLoop(ForStatement forStmt) {
        ASTNode parent = forStmt.getParent();
        while (parent != null) {
            if (parent instanceof EnhancedForStatement
                    || parent instanceof ForStatement
                    || parent instanceof WhileStatement
                    || parent instanceof DoStatement) {
                return true;
            }
            if (parent instanceof MethodDeclaration || parent instanceof TypeDeclaration) {
                break;
            }
            parent = parent.getParent();
        }
        return false;
    }
    
    /**
     * Checks if the loop body contains unconvertible statements.
     * Statements containing break, continue, or return cannot be converted to lambda.
     */
    private boolean containsUnconvertibleStatements(Statement body) {
        final boolean[] hasUnconvertible = {false};
        
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(BreakStatement node) {
                hasUnconvertible[0] = true;
                return false;
            }
            
            @Override
            public boolean visit(ContinueStatement node) {
                hasUnconvertible[0] = true;
                return false;
            }
            
            @Override
            public boolean visit(ReturnStatement node) {
                hasUnconvertible[0] = true;
                return false;
            }
        });
        
        return hasUnconvertible[0];
    }
    
    /**
     * Checks if the index variable is used in the loop body for complex patterns
     * that go beyond simple counter semantics.
     * 
     * <p>Detects the index variable participating in arithmetic InfixExpressions
     * (e.g., {@code i + 1}, {@code i - 1}, {@code i * 2}, {@code i % 2}).
     * This also catches array/list neighbor access patterns like {@code a[i+1]}
     * because the subscript expression {@code i+1} is itself an InfixExpression.</p>
     * 
     * <p>These patterns indicate the loop relies on index relationships between
     * iterations, which may not be safely convertible to stream operations.</p>
     * 
     * @param body the loop body statement
     * @param indexVarName the name of the index variable
     * @return true if the index is used in complex patterns
     * 
     * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
     */
    private boolean usesIndexBeyondSimpleAccess(Statement body, String indexVarName) {
        final boolean[] hasComplexUsage = {false};
        
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(InfixExpression node) {
                // Check if the index variable is used in arithmetic operations
                // like i+1, i-1, i*2, i%2
                if (isIndexInArithmeticExpression(node, indexVarName)) {
                    hasComplexUsage[0] = true;
                    return false;
                }
                return true;
            }
        });
        
        return hasComplexUsage[0];
    }
    
    /**
     * Checks if an InfixExpression uses the index variable in arithmetic.
     * Detects patterns: i+1, i-1, i*2, i%2, etc., including nested and chained
     * arithmetic expressions (e.g., (i + 1) + offset, multiplier * (i - 1)).
     */
    private boolean isIndexInArithmeticExpression(InfixExpression expr, String indexVarName) {
        if (!isArithmeticOperator(expr.getOperator())) {
            return false;
        }

        // Recursively inspect all operands (left, right, and extended) for
        // arithmetic usage of the index variable.
        if (containsIndexInArithmeticOperand(expr.getLeftOperand(), indexVarName)) {
            return true;
        }
        if (containsIndexInArithmeticOperand(expr.getRightOperand(), indexVarName)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        List<Expression> extended = expr.extendedOperands();
        for (Expression operand : extended) {
            if (containsIndexInArithmeticOperand(operand, indexVarName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given operator is an arithmetic operator
     * relevant for index arithmetic detection.
     */
    private boolean isArithmeticOperator(InfixExpression.Operator op) {
        return op == InfixExpression.Operator.PLUS
                || op == InfixExpression.Operator.MINUS
                || op == InfixExpression.Operator.TIMES
                || op == InfixExpression.Operator.DIVIDE
                || op == InfixExpression.Operator.REMAINDER;
    }

    /**
     * Recursively checks whether the given expression contains an arithmetic
     * use of the index variable.
     */
    private boolean containsIndexInArithmeticOperand(Expression expr, String indexVarName) {
        if (expr == null) {
            return false;
        }

        if (expr instanceof SimpleName) {
            return ((SimpleName) expr).getIdentifier().equals(indexVarName);
        }

        if (expr instanceof ParenthesizedExpression) {
            Expression inner = ((ParenthesizedExpression) expr).getExpression();
            return containsIndexInArithmeticOperand(inner, indexVarName);
        }

        if (expr instanceof InfixExpression infix) {
            if (!isArithmeticOperator(infix.getOperator())) {
                return false;
            }
            if (containsIndexInArithmeticOperand(infix.getLeftOperand(), indexVarName)) {
                return true;
            }
            if (containsIndexInArithmeticOperand(infix.getRightOperand(), indexVarName)) {
                return true;
            }
            @SuppressWarnings("unchecked")
            List<Expression> extended = infix.extendedOperands();
            for (Expression operand : extended) {
                if (containsIndexInArithmeticOperand(operand, indexVarName)) {
                    return true;
                }
            }
        }

        return false;
    }
    
    @Override
    public void rewrite(UseFunctionalCallFixCore upp, ForStatement visited,
                        CompilationUnitRewrite cuRewrite, TextEditGroup group,
                        ReferenceHolder<ASTNode, Object> data) throws CoreException {
        
        Object patternObj = data.get(visited);
        if (!(patternObj instanceof ForLoopPattern pattern)) {
            return;
        }
        
        AST ast = cuRewrite.getRoot().getAST();
        ASTRewrite rewrite = cuRewrite.getASTRewrite();
        
        // Build LoopModel
        String streamType = streamTypeName(visited, cuRewrite.getImportRewrite());
        if (streamType == null) return;
        LoopModel model = buildLoopModel(pattern, streamType);
        
        // Create renderer
        ASTStreamRenderer renderer = new ASTStreamRenderer(ast, rewrite, cuRewrite.getRoot(), pattern.body()) {
            @Override
            @SuppressWarnings("unchecked") //$NON-NLS-1$
            public Expression renderForEach(Expression pipeline, List<String> statements, String variable, boolean ordered) {
                // This handler moves the entire body. Copy original source ranges
                // instead of reparsing strings, which drops comments and statement kinds.
                MethodInvocation call = ast.newMethodInvocation();
                call.setExpression(pipeline);
                call.setName(ast.newSimpleName(ordered ? "forEachOrdered" : "forEach")); //$NON-NLS-1$ //$NON-NLS-2$
                LambdaExpression lambda = ast.newLambdaExpression();
                VariableDeclarationFragment parameter = ast.newVariableDeclarationFragment();
                parameter.setName(ast.newSimpleName(variable));
                lambda.parameters().add(parameter);
                lambda.setParentheses(false);
                Statement body = pattern.body();
                if (body instanceof Block block && block.statements().size() == 1
                        && block.statements().get(0) instanceof ExpressionStatement
                        && !containsComments(body, body.getStartPosition(), body.getStartPosition() + body.getLength())) {
                    body = (Statement) block.statements().get(0);
                }
                if (body instanceof ExpressionStatement statement) {
                    lambda.setBody(rewrite.createCopyTarget(statement.getExpression()));
                } else if (body instanceof Block) {
                    lambda.setBody(rewrite.createCopyTarget(body));
                } else {
                    Block block = ast.newBlock();
                    block.statements().add(rewrite.createCopyTarget(body));
                    lambda.setBody(block);
                }
                call.arguments().add(lambda);
                return call;
            }
        };
        
        // Transform using LoopModelTransformer
        LoopModelTransformer<Expression> transformer = new LoopModelTransformer<>(renderer);
        Expression streamExpression = transformer.transform(model);
        
        if (streamExpression != null) {
            // Wrap in ExpressionStatement
            ExpressionStatement newStatement = ast.newExpressionStatement(streamExpression);
            visited.setProperty(ASTNodes.UNTOUCH_COMMENT, Boolean.TRUE);
            rewrite.replace(visited, newStatement, group);
            
        }
    }
    
    /**
     * Builds a LoopModel from the analyzed ForLoopPattern.
     */
    private LoopModel buildLoopModel(ForLoopPattern pattern, String streamType) {
        // Get start and end expressions as strings
        String startStr = pattern.startExpr().toString();
        String endStr = pattern.endExpr().toString();
        
        // Adjust end expression for inclusive range (i <= end)
        if (pattern.inclusive()) {
            // For i <= end, we need IntStream.range(start, (end) + 1)
            // Parenthesize end expression to preserve operator precedence
            endStr = "(" + endStr + ") + 1";
        }
        
        // Build EXPLICIT_RANGE source descriptor
        String rangeExpression = startStr + "," + endStr;
        SourceDescriptor source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE,
            rangeExpression,
            "int", //$NON-NLS-1$
            // A complete factory preserves commas inside initializer calls.
            streamType + ".range(" + rangeExpression + ")" //$NON-NLS-1$ //$NON-NLS-2$
        );
        
        // Build element descriptor for the loop variable
        ElementDescriptor element = new ElementDescriptor(
            pattern.loopVarName(),
            "int",
            false // not a collection element
        );
        
        // Extract body statements and convert to strings
        List<String> bodyStatements = extractBodyStatementsAsStrings(pattern.body());
        
        // Build ForEachTerminal
        ForEachTerminal terminal = new ForEachTerminal(bodyStatements, false); // uses forEach (not forEachOrdered)
        
        // Build and return LoopModel
        return new LoopModelBuilder()
            .source(source)
            .element(element)
            .terminal(terminal)
            .build();
    }
    
    /**
     * Extracts statements from the loop body and converts them to expression strings.
     * Trailing semicolons are stripped because ForEachTerminal / ASTStreamRenderer.createExpression()
     * expects pure expressions, not statements.  This matches how
     * {@code JdtLoopExtractor.addSimpleForEachTerminal()} produces body strings for
     * the existing EnhancedForHandler.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractBodyStatementsAsStrings(Statement body) {
        return ExpressionHelper.bodyStatementsToStrings(body);
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return "IntStream.range(0, 10).forEach(i -> System.out.println(i));\n";
        }
        return "for (int i = 0; i < 10; i++)\n    System.out.println(i);\n";
    }
    
    /**
     * Immutable representation of an analyzed traditional for-loop pattern.
     *
     * @param loopVarName the loop variable name (e.g. {@code "i"})
     * @param startExpr the start expression (e.g. {@code 0})
     * @param endExpr the invariant end expression (e.g. {@code array.length})
     * @param inclusive {@code true} for {@code <=}, {@code false} for {@code <}
     * @param body the loop body statement
     */
    private record ForLoopPattern(String loopVarName, Expression startExpr, Expression endExpr,
                                  boolean inclusive, Statement body) {
    }
}
