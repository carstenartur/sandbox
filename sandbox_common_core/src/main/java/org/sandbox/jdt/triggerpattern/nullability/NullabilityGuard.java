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
package org.sandbox.jdt.triggerpattern.nullability;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Analyzes the nullability of expressions in Java AST using a four-stage model.
 * A non-null result is a conservative proof, not an inference from a reference
 * type or an annotation. Other results may be used as advisory evidence.
 *
 * <ol>
 *   <li><b>Stage 1</b>: Expressions whose value is intrinsically non-null</li>
 *   <li><b>Stage 2</b>: Initialization analysis (local data flow)</li>
 *   <li><b>Stage 3</b>: Contextual null-check analysis (SpotBugs-style)</li>
 *   <li><b>Stage 4</b>: Annotation-based checking ({@code @Nullable}/{@code @NonNull})</li>
 * </ol>
 *
 * @since 1.2.6
 */
public class NullabilityGuard {

	private static final String PROPERTIES_PATH = "org/sandbox/jdt/triggerpattern/nullability/non-null-types.properties"; //$NON-NLS-1$

	/** Known non-null factory methods (class -> set of method names). */
	private static final Map<String, Set<String>> NON_NULL_FACTORY_METHODS = Map.of(
			"java.nio.file.Paths", Set.of("get"), //$NON-NLS-1$ //$NON-NLS-2$
			"java.nio.file.Path", Set.of("of"), //$NON-NLS-1$ //$NON-NLS-2$
			"java.util.List", Set.of("of", "copyOf"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"java.util.Set", Set.of("of", "copyOf"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"java.util.Map", Set.of("of", "copyOf", "ofEntries", "entry"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"java.util.Objects", Set.of("requireNonNull", "requireNonNullElse", "requireNonNullElseGet"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"java.util.Collections", Set.of("unmodifiableList", "unmodifiableSet", "unmodifiableMap", "emptyList", "emptySet", "emptyMap", "singletonList", "singleton", "singletonMap"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
			"java.lang.String", Set.of("format", "join") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	);

	/** Nullable annotation simple names. */
	private static final Set<String> NULLABLE_ANNOTATIONS = Set.of(
			"Nullable", "CheckForNull"); //$NON-NLS-1$ //$NON-NLS-2$

	private final Map<String, String> nonNullTypes;

	/**
	 * Creates a guard with the bundled legacy type information.
	 */
	public NullabilityGuard() {
		this.nonNullTypes = loadWhitelist();
	}

	/**
	 * Creates a guard with legacy type information. A reference type alone is
	 * never used as proof of a non-null value.
	 *
	 * @param nonNullTypes map of fully-qualified type names to reasons
	 */
	public NullabilityGuard(Map<String, String> nonNullTypes) {
		this.nonNullTypes = new ConcurrentHashMap<>(nonNullTypes);
	}

	/**
	 * Analyzes the nullability of the given expression AST node.
	 *
	 * @param expression the expression to analyze (typically the receiver of a
	 *                   method call such as {@code x.toString()})
	 * @return a result containing the determined status, reason, and evidence
	 */
	public NullabilityResult analyze(Expression expression) {
		if (expression == null) {
			return new NullabilityResult(NullStatus.UNKNOWN, "null expression", List.of()); //$NON-NLS-1$
		}
		if ((expression.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
			return NullabilityResult.UNKNOWN;
		}
		if (expression instanceof ParenthesizedExpression parenthesized) {
			return analyze(parenthesized.getExpression());
		}

		// Stage 1: Intrinsically non-null values
		NullabilityResult stage1 = analyzeByType(expression);
		if (stage1.status() != NullStatus.UNKNOWN) {
			return stage1;
		}

		// Stage 2: Initialization analysis
		NullabilityResult stage2 = analyzeByInitialization(expression);
		if (stage2.status() != NullStatus.UNKNOWN) {
			return stage2;
		}

		// Stage 3: Contextual null-check analysis
		NullabilityResult stage3 = analyzeByNullChecks(expression);
		if (stage3.status() != NullStatus.UNKNOWN) {
			return stage3;
		}

		// Stage 4: Annotation-based
		NullabilityResult stage4 = analyzeByAnnotations(expression);
		if (stage4.status() != NullStatus.UNKNOWN) {
			return stage4;
		}

		return new NullabilityResult(NullStatus.UNKNOWN,
				"nullability could not be determined", List.of()); //$NON-NLS-1$
	}

	// ---- Stage 1: Intrinsically non-null values ----

	NullabilityResult analyzeByType(Expression expression) {
		// this expression is always non-null
		if (expression instanceof ThisExpression) {
			return new NullabilityResult(NullStatus.NON_NULL,
					"'this' reference is always non-null", List.of()); //$NON-NLS-1$
		}

		if (expression instanceof StringLiteral || expression instanceof TextBlock || expression instanceof TypeLiteral) {
			return new NullabilityResult(NullStatus.NON_NULL, "literal value is non-null", List.of()); //$NON-NLS-1$
		}

		ITypeBinding typeBinding = expression.resolveTypeBinding();
		if (typeBinding != null && !typeBinding.isRecovered()) {
			// Primitive types are never null
			if (typeBinding.isPrimitive()) {
				return new NullabilityResult(NullStatus.NON_NULL,
						"primitive type is never null", List.of()); //$NON-NLS-1$
			}
		}

		return NullabilityResult.UNKNOWN;
	}

	// ---- Stage 2: Initialization analysis ----

	NullabilityResult analyzeByInitialization(Expression expression) {
		// new Constructor() is always non-null
		if (expression instanceof ClassInstanceCreation || expression instanceof ArrayCreation) {
			return new NullabilityResult(NullStatus.NON_NULL,
					"'new' expression is always non-null", List.of()); //$NON-NLS-1$
		}

		// Check if expression is a method invocation on a known non-null factory
		if (expression instanceof MethodInvocation mi) {
			return analyzeMethodInvocation(mi);
		}

		// Check if it's a variable with a known initializer
		if (expression instanceof SimpleName name) {
			return analyzeVariableInitializer(name);
		}

		return NullabilityResult.UNKNOWN;
	}

	private NullabilityResult analyzeMethodInvocation(MethodInvocation mi) {
		IMethodBinding methodBinding = mi.resolveMethodBinding();
		if (methodBinding != null && !methodBinding.isRecovered()) {
			ITypeBinding declaringClass = methodBinding.getDeclaringClass();
			if (declaringClass != null) {
				String className = declaringClass.getQualifiedName();
				String methodName = methodBinding.getName();
				Set<String> methods = NON_NULL_FACTORY_METHODS.get(className);
				if (methods != null && methods.contains(methodName)) {
					return new NullabilityResult(NullStatus.NON_NULL,
							className + "." + methodName + "() is a known non-null factory method", //$NON-NLS-1$ //$NON-NLS-2$
							List.of());
				}
			}
		}

		return NullabilityResult.UNKNOWN;
	}

	private NullabilityResult analyzeVariableInitializer(SimpleName name) {
		IVariableBinding varBinding = resolveVariableBinding(name);
		if (!isStableLocal(varBinding)) {
			return NullabilityResult.UNKNOWN;
		}

		// Search the whole enclosing executable, including outer lexical blocks.
		ASTNode scope = findDeclarationScope(name);
		if (scope == null) {
			return NullabilityResult.UNKNOWN;
		}

		// Look for variable declaration with initializer
		String varName = name.getIdentifier();
		VariableInitializerFinder finder = new VariableInitializerFinder(varBinding);
		scope.accept(finder);

		if (finder.initializer != null) {
			// new X() → non-null
			if (finder.initializer instanceof ClassInstanceCreation) {
				return new NullabilityResult(NullStatus.NON_NULL,
						"variable '" + varName + "' initialized with 'new'", //$NON-NLS-1$ //$NON-NLS-2$
						List.of());
			}
			// Known factory method → non-null
			if (finder.initializer instanceof MethodInvocation mi) {
				NullabilityResult initResult = analyzeMethodInvocation(mi);
				if (initResult.status() == NullStatus.NON_NULL) {
					return new NullabilityResult(NullStatus.NON_NULL,
							"variable '" + varName + "' initialized with non-null factory: " + initResult.reason(), //$NON-NLS-1$ //$NON-NLS-2$
							List.of());
				}
			}
		}

		// Check if initializer is a Map.get() call → nullable
		if (finder.initializer != null && isCollectionGetCall(finder.initializer)) {
			return new NullabilityResult(NullStatus.NULLABLE,
					"variable may be result of Map.get() which can return null", //$NON-NLS-1$
					List.of());
		}

		return NullabilityResult.UNKNOWN;
	}

	private boolean isCollectionGetCall(Expression initializer) {
		if (!(initializer instanceof MethodInvocation mi)) {
			return false;
		}

		String methodName = mi.getName().getIdentifier();
		if (!"get".equals(methodName)) { //$NON-NLS-1$
			return false;
		}

		Expression receiver = mi.getExpression();
		if (receiver == null) {
			return false;
		}

		ITypeBinding receiverType = receiver.resolveTypeBinding();
		if (receiverType == null) {
			return false;
		}

		String typeName = receiverType.getQualifiedName();
		return typeName.startsWith("java.util.Map") //$NON-NLS-1$
				|| typeName.startsWith("java.util.HashMap") //$NON-NLS-1$
				|| typeName.startsWith("java.util.LinkedHashMap") //$NON-NLS-1$
				|| typeName.startsWith("java.util.TreeMap"); //$NON-NLS-1$
	}

	// ---- Stage 3: Contextual null-check analysis (SpotBugs-style) ----

	NullabilityResult analyzeByNullChecks(Expression expression) {
		if (!(expression instanceof SimpleName name)) {
			return NullabilityResult.UNKNOWN;
		}

		String varName = name.getIdentifier();
		IVariableBinding varBinding = resolveVariableBinding(name);
		if (varBinding == null || varBinding.isRecovered()) {
			return NullabilityResult.UNKNOWN;
		}
		ASTNode enclosingMethod = findEnclosingMethod(expression);
		if (enclosingMethod == null) {
			return NullabilityResult.UNKNOWN;
		}

		int expressionOffset = expression.getStartPosition();
		NullCheckFinder finder = new NullCheckFinder(varBinding);
		enclosingMethod.accept(finder);

		if (finder.nullCheckLocations.isEmpty()) {
			return NullabilityResult.UNKNOWN;
		}

		List<String> evidence = new ArrayList<>();
		boolean hasCheckBefore = false;
		boolean hasCheckAfter = false;
		boolean isInsideGuard = false;

		for (NullCheckLocation loc : finder.nullCheckLocations) {
			evidence.add("line " + loc.line + ": " + loc.description); //$NON-NLS-1$ //$NON-NLS-2$
			if (loc.offset < expressionOffset) {
				hasCheckBefore = true;
				// Check if we are inside a guarded block (e.g., if (x != null) { ...here... })
				if (isInsideNullGuard(expression, varBinding)) {
					isInsideGuard = true;
				}
			}
			if (loc.offset > expressionOffset) {
				hasCheckAfter = true;
			}
		}

		if (isInsideGuard) {
			// Expression is inside a null guard → safe in this context
			return new NullabilityResult(NullStatus.NON_NULL,
					"variable '" + varName + "' is inside a null guard", //$NON-NLS-1$ //$NON-NLS-2$
					evidence);
		}

		if (hasCheckAfter && !hasCheckBefore) {
			// SpotBugs-style: null check AFTER the usage → high risk
			return new NullabilityResult(NullStatus.NULLABLE,
					"null-check for '" + varName + "' found after usage (SpotBugs-style: null is realistic)", //$NON-NLS-1$ //$NON-NLS-2$
					evidence);
		}

		if (hasCheckBefore && !isInsideGuard) {
			// There's a check before but we're not inside its guard
			return new NullabilityResult(NullStatus.POTENTIALLY_NULLABLE,
					"null-check for '" + varName + "' exists nearby but usage is not inside guard", //$NON-NLS-1$ //$NON-NLS-2$
					evidence);
		}

		// Null check exists somewhere → the type can be null
		return new NullabilityResult(NullStatus.POTENTIALLY_NULLABLE,
				"null-check for '" + varName + "' found in same method", //$NON-NLS-1$ //$NON-NLS-2$
				evidence);
	}

	/**
	 * Checks if the given expression is inside a block guarded by a null check
	 * for the same stable local variable or parameter.
	 */
	private boolean isInsideNullGuard(Expression expression, IVariableBinding binding) {
		if (!isStableLocal(binding)) {
			return false;
		}
		ASTNode current = expression.getParent();
		while (current != null) {
			if (current instanceof IfStatement ifStmt) {
				Expression condition = ifStmt.getExpression();
				if (isNonNullCheck(condition, binding)) {
					// Check if expression is in the then-branch
					Statement thenBranch = ifStmt.getThenStatement();
					if (isAncestor(thenBranch, expression)) {
						return true;
					}
				}
			}
			current = current.getParent();
		}
		return false;
	}

	private boolean isNonNullCheck(Expression condition, IVariableBinding binding) {
		while (condition instanceof ParenthesizedExpression parenthesized) {
			condition= parenthesized.getExpression();
		}
		if (condition instanceof InfixExpression infix) {
			if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS && !infix.hasExtendedOperands()) {
				return isNullComparisonWith(infix, binding);
			}
		}
		return false;
	}

	private boolean isNullComparisonWith(InfixExpression infix, IVariableBinding binding) {
		Expression left = infix.getLeftOperand();
		Expression right = infix.getRightOperand();
		return (isBindingMatch(left, binding) && right instanceof NullLiteral)
				|| (isBindingMatch(right, binding) && left instanceof NullLiteral);
	}

	private static boolean isBindingMatch(Expression expr, IVariableBinding binding) {
		if (expr instanceof SimpleName name) {
			IVariableBinding candidate= resolveVariableBinding(name);
			return candidate != null && !candidate.isRecovered()
					&& candidate.getVariableDeclaration().isEqualTo(binding.getVariableDeclaration());
		}
		return false;
	}

	private boolean isAncestor(ASTNode ancestor, ASTNode descendant) {
		ASTNode current = descendant;
		while (current != null) {
			if (current == ancestor) {
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	// ---- Stage 4: Annotation-based checking ----

	NullabilityResult analyzeByAnnotations(Expression expression) {
		ITypeBinding typeBinding = expression.resolveTypeBinding();
		if (typeBinding == null) {
			return NullabilityResult.UNKNOWN;
		}

		// Check annotations on the type binding
		for (IAnnotationBinding annotation : typeBinding.getTypeAnnotations()) {
			String annotName = annotation.getAnnotationType().getName();
			if (NULLABLE_ANNOTATIONS.contains(annotName)) {
				return new NullabilityResult(NullStatus.NULLABLE,
						"@" + annotName + " annotation found", //$NON-NLS-1$ //$NON-NLS-2$
						List.of());
			}
		}

		// Check if it's a variable → check variable annotations
		if (expression instanceof SimpleName name) {
			IVariableBinding varBinding = resolveVariableBinding(name);
			if (varBinding != null) {
				for (IAnnotationBinding annotation : varBinding.getAnnotations()) {
					String annotName = annotation.getAnnotationType().getName();
					if (NULLABLE_ANNOTATIONS.contains(annotName)) {
						return new NullabilityResult(NullStatus.NULLABLE,
								"@" + annotName + " annotation on variable", //$NON-NLS-1$ //$NON-NLS-2$
								List.of());
					}
				}
			}
		}

		// Check method return annotations for method invocations
		if (expression instanceof MethodInvocation mi) {
			IMethodBinding methodBinding = mi.resolveMethodBinding();
			if (methodBinding != null) {
				for (IAnnotationBinding annotation : methodBinding.getAnnotations()) {
					String annotName = annotation.getAnnotationType().getName();
					if (NULLABLE_ANNOTATIONS.contains(annotName)) {
						return new NullabilityResult(NullStatus.NULLABLE,
								"method return annotated @" + annotName, //$NON-NLS-1$
								List.of());
					}
				}
			}
		}

		return NullabilityResult.UNKNOWN;
	}

	// ---- Helper methods and inner classes ----

	private static IVariableBinding resolveVariableBinding(SimpleName name) {
		if (name.resolveBinding() instanceof IVariableBinding vb) {
			return vb;
		}
		return null;
	}

	private static boolean isStableLocal(IVariableBinding binding) {
		return binding != null && !binding.isRecovered() && !binding.isField()
				&& (Modifier.isFinal(binding.getModifiers()) || binding.isEffectivelyFinal());
	}

	private static ASTNode findDeclarationScope(ASTNode node) {
		ASTNode result= null;
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration || current instanceof Initializer) {
				result= current;
			}
			current = current.getParent();
		}
		return result;
	}

	private static ASTNode findEnclosingMethod(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration) {
				return current;
			}
			current = current.getParent();
		}
		return null;
	}

	private Map<String, String> loadWhitelist() {
		Properties props = new Properties();
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(PROPERTIES_PATH)) {
			if (is != null) {
				props.load(is);
			}
		} catch (IOException e) {
			// Fall through with empty properties
		}
		Map<String, String> result = new ConcurrentHashMap<>();
		for (String key : props.stringPropertyNames()) {
			result.put(key, props.getProperty(key));
		}
		return result;
	}

	/**
	 * Returns the legacy type information loaded from configuration. A reference
	 * type alone is not proof that a particular value is non-null.
	 *
	 * @return unmodifiable view of the whitelist
	 */
	public Map<String, String> getNonNullTypes() {
		return Map.copyOf(nonNullTypes);
	}

	/** Records a location where a null check was found. */
	record NullCheckLocation(int offset, int line, String description) {}

	/**
	 * Visitor that finds null checks for a specific variable within a method body.
	 */
	private static class NullCheckFinder extends ASTVisitor {
		private final IVariableBinding binding;
		private final String varName;
		final List<NullCheckLocation> nullCheckLocations = new ArrayList<>();

		NullCheckFinder(IVariableBinding binding) {
			this.binding= binding;
			this.varName= binding.getName();
		}

		@Override
		public boolean visit(InfixExpression node) {
			InfixExpression.Operator op = node.getOperator();
			if (op == InfixExpression.Operator.EQUALS || op == InfixExpression.Operator.NOT_EQUALS) {
				Expression left = node.getLeftOperand();
				Expression right = node.getRightOperand();
				boolean leftIsVar = isNameMatch(left);
				boolean rightIsVar = isNameMatch(right);
				boolean leftIsNull = left instanceof NullLiteral;
				boolean rightIsNull = right instanceof NullLiteral;

				if ((leftIsVar && rightIsNull) || (rightIsVar && leftIsNull)) {
					ASTNode root = node.getRoot();
					int line = -1;
					if (root instanceof org.eclipse.jdt.core.dom.CompilationUnit cu) {
						line = cu.getLineNumber(node.getStartPosition());
					}
					String desc = varName + " " + op + " null"; //$NON-NLS-1$ //$NON-NLS-2$
					nullCheckLocations.add(new NullCheckLocation(node.getStartPosition(), line, desc));
				}
			}
			return true;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			// Objects.requireNonNull(variable)
			if ("requireNonNull".equals(node.getName().getIdentifier())) { //$NON-NLS-1$
				@SuppressWarnings("unchecked")
				List<Expression> args = node.arguments();
				if (!args.isEmpty() && isNameMatch(args.get(0))) {
					ASTNode root = node.getRoot();
					int line = -1;
					if (root instanceof org.eclipse.jdt.core.dom.CompilationUnit cu) {
						line = cu.getLineNumber(node.getStartPosition());
					}
					nullCheckLocations.add(new NullCheckLocation(node.getStartPosition(), line,
							"Objects.requireNonNull(" + varName + ")")); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			// Optional.ofNullable(variable)
			if ("ofNullable".equals(node.getName().getIdentifier())) { //$NON-NLS-1$
				@SuppressWarnings("unchecked")
				List<Expression> args = node.arguments();
				if (!args.isEmpty() && isNameMatch(args.get(0))) {
					ASTNode root = node.getRoot();
					int line = -1;
					if (root instanceof org.eclipse.jdt.core.dom.CompilationUnit cu) {
						line = cu.getLineNumber(node.getStartPosition());
					}
					nullCheckLocations.add(new NullCheckLocation(node.getStartPosition(), line,
							"Optional.ofNullable(" + varName + ")")); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			return true;
		}

		@Override
		public boolean visit(ConditionalExpression node) {
			// variable != null ? ... : ...
			Expression condition = node.getExpression();
			if (condition instanceof InfixExpression infix) {
				InfixExpression.Operator op = infix.getOperator();
				if (op == InfixExpression.Operator.EQUALS || op == InfixExpression.Operator.NOT_EQUALS) {
					Expression left = infix.getLeftOperand();
					Expression right = infix.getRightOperand();
					boolean leftIsVar = isNameMatch(left);
					boolean rightIsVar = isNameMatch(right);
					boolean leftIsNull = left instanceof NullLiteral;
					boolean rightIsNull = right instanceof NullLiteral;

					if ((leftIsVar && rightIsNull) || (rightIsVar && leftIsNull)) {
						ASTNode root = node.getRoot();
						int line = -1;
						if (root instanceof org.eclipse.jdt.core.dom.CompilationUnit cu) {
							line = cu.getLineNumber(node.getStartPosition());
						}
						nullCheckLocations.add(new NullCheckLocation(node.getStartPosition(), line,
								varName + " " + op + " null (ternary)")); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
			return true;
		}

		private boolean isNameMatch(Expression expr) {
			return isBindingMatch(expr, binding);
		}
	}

	/**
	 * Visitor that finds variable declarations with initializers.
	 */
	private static class VariableInitializerFinder extends ASTVisitor {
		private final IVariableBinding binding;
		Expression initializer;

		VariableInitializerFinder(IVariableBinding binding) {
			this.binding = binding;
		}

		@Override
		public boolean visit(VariableDeclarationStatement node) {
			@SuppressWarnings("unchecked")
			List<VariableDeclarationFragment> fragments = node.fragments();
			for (VariableDeclarationFragment frag : fragments) {
				if (isBindingMatch(frag.getName(), binding) && frag.getInitializer() != null) {
					this.initializer = frag.getInitializer();
					return false;
				}
			}
			return true;
		}

		@Override
		public boolean visit(SingleVariableDeclaration node) {
			if (isBindingMatch(node.getName(), binding) && node.getInitializer() != null) {
				this.initializer = node.getInitializer();
				return false;
			}
			return true;
		}
	}
}
