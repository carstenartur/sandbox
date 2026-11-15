/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_ASSERT;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_ASSUME;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_ASSERTIONS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_ASSUMPTIONS;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.sandbox.jdt.cleanup.multifile.RelatedCompilationUnitSearch;

/**
 * Discovers narrow source-owned assertion and assumption helper wrappers whose
 * complete caller closure can be delegated to the shared multi-file search.
 *
 * <p>The first supported contract is deliberately conservative: one private
 * helper, or one package-private static helper, with a {@code void} body that
 * consists of exactly one direct JUnit 4 {@code Assert} or {@code Assume}
 * invocation. Public/protected helpers and overridable package-private instance
 * methods remain outside automatic migration. A strict single-option profile also follows
 * exact-dispatch boolean helper graphs, including recursive branches. Every participating
 * method requires a complete caller search.</p>
 */
public final class JUnitSharedHelperScopeDetector {

	private enum HelperChecks {
		ASSERTIONS(ORG_JUNIT_ASSERT, ORG_JUNIT_JUPITER_API_ASSERTIONS, Set.of("assertTrue", "assertFalse")), //$NON-NLS-1$ //$NON-NLS-2$
		ASSUMPTIONS(ORG_JUNIT_ASSUME, ORG_JUNIT_JUPITER_API_ASSUMPTIONS, Set.of("assumeTrue", "assumeFalse")); //$NON-NLS-1$ //$NON-NLS-2$

		final String source;
		final String target;
		final Set<String> methods;

		HelperChecks(String source, String target, Set<String> methods) {
			this.source= source;
			this.target= target;
			this.methods= methods;
		}

		boolean owns(ITypeBinding type) {
			return type != null && !type.isRecovered() && source.equals(type.getErasure().getQualifiedName());
		}
	}

	private record HelperCandidate(IMethod method, String bindingKey, ICompilationUnit compilationUnit) {
	}

	/** Ordinary helper seeds and exact-dispatch components requiring complete reverse-reference search. */
	public record HelperSearchSeeds(JUnitScopeCandidateDetector.SearchSeeds ordinary,
			List<List<IJavaElement>> assertionPaths) {
		public HelperSearchSeeds {
			assertionPaths= assertionPaths.stream().map(List::copyOf).toList();
		}
	}

	private JUnitSharedHelperScopeDetector() {
	}

	/**
	 * Finds direct assertion and assumption wrapper methods from declarations and
	 * calls in the current scope.
	 */
	public static JUnitScopeCandidateDetector.SearchSeeds findSearchSeeds(IJavaProject project,
			Collection<ICompilationUnit> currentScope, boolean migrateAssertions,
			boolean migrateAssumptions, IProgressMonitor monitor) {
		return findSearchSeeds(project, currentScope, migrateAssertions, migrateAssumptions, false, monitor).ordinary();
	}

	/** Also traces eligible assertion paths with statically known targets when the cleanup profile allows it. */
	public static HelperSearchSeeds findSearchSeeds(IJavaProject project,
			Collection<ICompilationUnit> currentScope, boolean migrateAssertions,
			boolean migrateAssumptions, boolean allowAssertionPaths, IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()
				|| !migrateAssertions && !migrateAssumptions) {
			return new HelperSearchSeeds(empty(), List.of());
		}
		checkCanceled(monitor);
		List<ICompilationUnit> selected= normalize(project, currentScope);
		if (selected.isEmpty()) {
			return new HelperSearchSeeds(empty(), List.of());
		}

		Map<String, HelperCandidate> possible= new LinkedHashMap<>();
		Set<String> invocationCandidates= new LinkedHashSet<>();
		Map<String, HelperCandidate> verified= new LinkedHashMap<>();
		Set<List<IJavaElement>> assertionPaths= new LinkedHashSet<>();
		HelperChecks checks= migrateAssertions ? HelperChecks.ASSERTIONS : HelperChecks.ASSUMPTIONS;
		boolean followAssertionPaths= allowAssertionPaths && (migrateAssertions != migrateAssumptions)
				&& available(project, checks.target);
		Map<String, CompilationUnit> selectedRoots= parse(project, selected, monitor);
		for (CompilationUnit root : selectedRoots.values()) {
			collect(root, migrateAssertions, migrateAssumptions,
					followAssertionPaths, possible, invocationCandidates, verified);
		}

		Map<String, CompilationUnit> roots= new LinkedHashMap<>(selectedRoots);
		Set<ICompilationUnit> declarationUnits= new LinkedHashSet<>();
		for (HelperCandidate candidate : possible.values()) {
			if (!roots.containsKey(candidate.compilationUnit().getHandleIdentifier())) {
				declarationUnits.add(candidate.compilationUnit());
			}
		}
		if (!declarationUnits.isEmpty()) {
			roots.putAll(parseByProject(declarationUnits, monitor));
		}
		for (HelperCandidate candidate : possible.values()) {
			checkCanceled(monitor);
			CompilationUnit root= roots.get(candidate.compilationUnit().getHandleIdentifier());
			if (root == null || !(root.findDeclaringNode(candidate.bindingKey()) instanceof MethodDeclaration method)) {
				continue;
			}
			if (invocationCandidates.contains(candidate.bindingKey())
					&& isDirectWrapper(method, migrateAssertions, migrateAssumptions)) {
				verified.putIfAbsent(candidate.bindingKey(), candidate);
			} else if (followAssertionPaths && !verified.containsKey(candidate.bindingKey())) {
				List<IJavaElement> path= assertionPath(candidate, roots, checks, monitor);
				if (!path.isEmpty()) {
					assertionPaths.add(path);
				}
			}
		}
		checkCanceled(monitor);
		if (verified.isEmpty()) {
			return new HelperSearchSeeds(empty(), List.copyOf(assertionPaths));
		}

		List<IJavaElement> elements= new ArrayList<>();
		Set<ICompilationUnit> directUnits= new LinkedHashSet<>();
		for (HelperCandidate helper : verified.values()) {
			elements.add(helper.method());
			directUnits.add(helper.compilationUnit());
		}
		return new HelperSearchSeeds(new JUnitScopeCandidateDetector.SearchSeeds(true, true, elements,
				new ArrayList<>(directUnits)), List.copyOf(assertionPaths));
	}

	/** Returns complete source closures whose newly admitted units support the assertion rewrite. */
	public static List<ICompilationUnit> closeAssertionPaths(Collection<IJavaProject> projects, List<List<IJavaElement>> paths,
			Collection<ICompilationUnit> existingScope, Collection<ICompilationUnit> allowedUnits,
			boolean migrateAssertions, IProgressMonitor monitor) throws CoreException {
		HelperChecks checks= migrateAssertions ? HelperChecks.ASSERTIONS : HelperChecks.ASSUMPTIONS;
		Set<ICompilationUnit> approved= new LinkedHashSet<>();
		Set<ICompilationUnit> existing= new LinkedHashSet<>(existingScope);
		for (List<IJavaElement> path : paths) {
			RelatedCompilationUnitSearch.Result closure= RelatedCompilationUnitSearch.findReferences(
					projects, path, List.of(), allowedUnits, monitor);
			if (!closure.complete()) {
				continue;
			}
			List<ICompilationUnit> added= closure.compilationUnits().stream()
					.filter(unit -> !existing.contains(unit) && !approved.contains(unit)).toList();
			if (added.isEmpty()) {
				continue;
			}
			if (added.stream().map(ICompilationUnit::getJavaProject).distinct().anyMatch(project -> !available(project, checks.target))) {
				continue;
			}
			Map<String, CompilationUnit> roots= parseByProject(added, monitor);
			if (roots.size() == added.size() && roots.values().stream().allMatch(root -> hasSafeRewrites(root, checks))) {
				approved.addAll(added);
			}
		}
		checkCanceled(monitor);
		return List.copyOf(approved);
	}

	private static void collect(CompilationUnit root, boolean migrateAssertions,
			boolean migrateAssumptions, boolean followMethodReferences, Map<String, HelperCandidate> possible,
			Set<String> invocationCandidates, Map<String, HelperCandidate> verified) {
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding= declaration(node.resolveBinding());
				HelperCandidate candidate= candidate(binding);
				if (candidate != null && isDirectWrapper(node, migrateAssertions, migrateAssumptions)) {
					verified.putIfAbsent(candidate.bindingKey(), candidate);
				}
				return true;
			}

			@Override
			public boolean visit(MethodInvocation node) {
				collectCandidate(declaration(node.resolveMethodBinding()), false);
				return true;
			}

			@Override
			public boolean visit(ExpressionMethodReference node) {
				if (followMethodReferences) {
					collectCandidate(exactDispatchDeclaration(node.resolveMethodBinding()), true);
				}
				return true;
			}

			@Override
			public boolean visit(TypeMethodReference node) {
				if (followMethodReferences) {
					collectCandidate(exactDispatchDeclaration(node.resolveMethodBinding()), true);
				}
				return true;
			}

			private void collectCandidate(IMethodBinding method, boolean fromMethodReference) {
				HelperCandidate candidate= candidate(method);
				if (candidate != null) {
					possible.putIfAbsent(candidate.bindingKey(), candidate);
					if (!fromMethodReference) {
						invocationCandidates.add(candidate.bindingKey());
					}
				}
			}
		});
	}

	/** Proves every edge of an exact-dispatch helper component before admitting its caller closure. */
	private static List<IJavaElement> assertionPath(HelperCandidate seed,
			Map<String, CompilationUnit> roots, HelperChecks checks, IProgressMonitor monitor) {
		var pending= new ArrayDeque<HelperCandidate>();
		pending.add(seed);
		Map<String, IJavaElement> visited= new LinkedHashMap<>();
		boolean hasTerminal= false;
		while (!pending.isEmpty()) {
			checkCanceled(monitor);
			HelperCandidate current= pending.removeFirst();
			if (visited.putIfAbsent(current.bindingKey(), current.method()) != null) {
				continue;
			}
			ICompilationUnit unit= current.compilationUnit();
			String handle= unit.getHandleIdentifier();
			if (!roots.containsKey(handle)) {
				roots.putAll(parse(unit.getJavaProject(), List.of(unit), monitor));
			}
			CompilationUnit root= roots.get(handle);
			if (root == null || !(root.findDeclaringNode(current.bindingKey()) instanceof MethodDeclaration method)
					|| !hasExactDispatch(method.getModifiers()) || method.getBody() == null
					|| Arrays.stream(root.getProblems()).anyMatch(IProblem::isError)) {
				return List.of();
			}
			List<MethodInvocation> calls= new ArrayList<>();
			if (!collectHelperCalls(method.getBody(), calls) || calls.isEmpty()) {
				return List.of();
			}
			for (MethodInvocation call : calls) {
				IMethodBinding target= exactDispatchDeclaration(call.resolveMethodBinding());
				if (target == null) {
					return List.of();
				}
				if (checks.owns(target.getDeclaringClass())) {
					if (!isSafeCheck(call, target, checks)) {
						return List.of();
					}
					hasTerminal= true;
				} else {
					HelperCandidate next= candidate(target);
					if (next == null) {
						return List.of();
					}
					pending.addLast(next);
				}
			}
		}
		return hasTerminal ? List.copyOf(visited.values()) : List.of();
	}

	/** Sequences and branches retain order, conditions and dispatch; only proven call leaves can rewrite. */
	private static boolean collectHelperCalls(Statement statement, List<MethodInvocation> calls) {
		if (statement instanceof Block block) {
			for (Object child : block.statements()) {
				if (!collectHelperCalls((Statement) child, calls)) {
					return false;
				}
			}
			return true;
		}
		if (statement instanceof IfStatement branch) {
			Expression condition= branch.getExpression();
			return (condition instanceof BooleanLiteral || isParameterRead(condition, "boolean")) //$NON-NLS-1$
					&& collectHelperCalls(branch.getThenStatement(), calls)
					&& (branch.getElseStatement() == null || collectHelperCalls(branch.getElseStatement(), calls));
		}
		if (statement instanceof ExpressionStatement expression && expression.getExpression() instanceof MethodInvocation call) {
			// Simple argument reads cannot hide another invocation or deferred callback.
			for (Object argument : call.arguments()) {
				if (!(argument instanceof SimpleName || argument instanceof BooleanLiteral || argument instanceof StringLiteral || argument instanceof NumberLiteral)) {
					return false;
				}
			}
			Expression receiver= call.getExpression();
			if (receiver != null && !(receiver instanceof Name || receiver instanceof ThisExpression)) {
				return false;
			}
			calls.add(call);
			return true;
		}
		return false;
	}

	private static boolean isDirectWrapper(MethodDeclaration method, boolean migrateAssertions,
			boolean migrateAssumptions) {
		MethodInvocation invocation= directInvocation(method);
		if (invocation == null) {
			return false;
		}
		IMethodBinding invoked= declaration(invocation.resolveMethodBinding());
		ITypeBinding owner= invoked == null ? null : invoked.getDeclaringClass();
		String ownerName= owner == null ? null : owner.getErasure().getQualifiedName();
		return migrateAssertions && ORG_JUNIT_ASSERT.equals(ownerName)
				|| migrateAssumptions && ORG_JUNIT_ASSUME.equals(ownerName);
	}

	private static MethodInvocation directInvocation(MethodDeclaration method) {
		IMethodBinding helper= declaration(method.resolveBinding());
		if (helper == null || helper.isConstructor() || method.getBody() == null
				|| !"void".equals(helper.getReturnType().getQualifiedName()) //$NON-NLS-1$
				|| method.getBody().statements().size() != 1
				|| !(method.getBody().statements().get(0) instanceof ExpressionStatement statement)
				|| !(statement.getExpression() instanceof MethodInvocation invocation)) {
			return null;
		}
		return invocation;
	}

	private static boolean hasSafeRewrites(CompilationUnit root, HelperChecks checks) {
		// The local assertion cleanup rewrites every matching call and import in the
		// newly admitted unit. Only directly qualified calls with verified argument evaluation are eligible.
		for (Object item : root.imports()) {
			ImportDeclaration imported= (ImportDeclaration) item;
			String name= imported.getName().getFullyQualifiedName();
			if (checks.source.equals(name) || name.startsWith(checks.source + ".") //$NON-NLS-1$
					|| imported.isOnDemand() && "org.junit".equals(name)) { //$NON-NLS-1$
				return false;
			}
		}
		AssertionRewriteCheck check= new AssertionRewriteCheck(checks);
		root.accept(check);
		return check.safe;
	}

	private static final class AssertionRewriteCheck extends ASTVisitor {
		private boolean safe= true;
		private final HelperChecks checks;

		AssertionRewriteCheck(HelperChecks checks) {
			this.checks= checks;
		}

		@Override
		public boolean preVisit2(ASTNode node) {
			return safe;
		}

		@Override
		public boolean visit(MethodInvocation invocation) {
			IMethodBinding method= invocation.resolveMethodBinding();
			if (method == null || method.isRecovered()) {
				safe= false;
			} else if (checks.owns(method.getDeclaringClass())) {
				safe= isSafeCheck(invocation, method, checks);
			}
			return safe;
		}
	}

	private static boolean isSafeCheck(MethodInvocation invocation, IMethodBinding method, HelperChecks checks) {
		if (!(invocation.getExpression() instanceof Name qualifier)
				|| !checks.source.equals(qualifier.getFullyQualifiedName())
				|| !(qualifier.resolveBinding() instanceof ITypeBinding type) || !checks.owns(type)
				|| !Modifier.isStatic(method.getModifiers())
				|| !(invocation.getParent() instanceof ExpressionStatement)) {
			return false;
		}
		ITypeBinding[] parameters= method.getParameterTypes();
		if (checks == HelperChecks.ASSERTIONS && "assertEquals".equals(method.getName())) { //$NON-NLS-1$
			return safeDeltaEquality(invocation, parameters);
		}
		if (!checks.methods.contains(method.getName())) {
			return false;
		}
		if (parameters.length < 1 || parameters.length > 2
				|| parameters.length != invocation.arguments().size()) {
			return false;
		}
		Expression condition= (Expression) invocation.arguments().getLast();
		if (!(condition instanceof BooleanLiteral || isParameterRead(condition, "boolean"))) { //$NON-NLS-1$
			return false;
		}
		if (parameters.length == 1) {
			return true;
		}
		Expression message= (Expression) invocation.arguments().getFirst();
		return message instanceof StringLiteral || isParameterRead(message, "java.lang.String"); //$NON-NLS-1$
	}

	/** Strictly positive, finite literal tolerances avoid JUnit 4/Jupiter validation differences. */
	private static boolean safeDeltaEquality(MethodInvocation invocation, ITypeBinding[] parameters) {
		if ((parameters.length != 3 && parameters.length != 4) || invocation.arguments().size() != parameters.length) {
			return false;
		}
		int first= parameters.length - 3;
		if (first != 0) {
			Expression message= (Expression) invocation.arguments().getFirst();
			if (!"java.lang.String".equals(parameters[0].getQualifiedName()) //$NON-NLS-1$
					|| !(message instanceof StringLiteral || isParameterRead(message, "java.lang.String"))) { //$NON-NLS-1$
				return false;
			}
		}
		String numericType= parameters[first].getQualifiedName();
		if (!"double".equals(numericType) && !"float".equals(numericType)) { //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		for (int i= first; i < parameters.length; i++) {
			Expression argument= (Expression) invocation.arguments().get(i);
			if (!numericType.equals(parameters[i].getQualifiedName())
					|| !(argument instanceof NumberLiteral || isParameterRead(argument, numericType))
					|| argument.resolveTypeBinding() == null
					|| !numericType.equals(argument.resolveTypeBinding().getQualifiedName())) {
				return false;
			}
		}
		Expression delta= (Expression) invocation.arguments().getLast();
		return delta instanceof NumberLiteral && delta.resolveConstantExpressionValue() instanceof Number value
				&& value.doubleValue() > 0 && Double.isFinite(value.doubleValue());
	}

	private static boolean isParameterRead(Expression expression, String typeName) {
		return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding variable
				&& !variable.isRecovered() && variable.isParameter() && !variable.getType().isRecovered()
				&& typeName.equals(variable.getType().getQualifiedName());
	}

	private static boolean available(IJavaProject project, String target) {
		try {
			IType type= project.findType(target);
			return type != null && type.exists();
		} catch (JavaModelException e) {
			return false;
		}
	}

	private static HelperCandidate candidate(IMethodBinding binding) {
		if (!eligible(binding)) {
			return null;
		}
		IJavaElement javaElement= binding.getJavaElement();
		if (!(javaElement instanceof IMethod method) || !method.exists() || method.isBinary()) {
			return null;
		}
		IJavaElement ancestor= method.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (!(ancestor instanceof ICompilationUnit unit) || !unit.exists()) {
			return null;
		}
		return new HelperCandidate(method, binding.getKey(), unit.getPrimary());
	}

	private static boolean eligible(IMethodBinding binding) {
		if (binding == null || binding.isConstructor()) {
			return false;
		}
		int modifiers= binding.getModifiers();
		if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
			return false;
		}
		return hasExactDispatch(modifiers);
	}

	private static IMethodBinding declaration(IMethodBinding binding) {
		return binding == null ? null : binding.getMethodDeclaration();
	}

	private static IMethodBinding exactDispatchDeclaration(IMethodBinding binding) {
		IMethodBinding method= binding == null || binding.isRecovered() ? null : binding.getMethodDeclaration();
		return method != null && !method.isRecovered() && method.getKey() != null
				&& hasExactDispatch(method.getModifiers()) ? method : null;
	}

	private static boolean hasExactDispatch(int modifiers) {
		return Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers);
	}

	private static List<ICompilationUnit> normalize(IJavaProject project,
			Collection<ICompilationUnit> units) {
		Map<String, ICompilationUnit> result= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				ICompilationUnit primary= unit.getPrimary();
				result.put(primary.getHandleIdentifier(), primary);
			}
		}
		return new ArrayList<>(result.values());
	}

	/** Parse source in its declaring project's binding environment and compiler options. */
	private static Map<String, CompilationUnit> parseByProject(Collection<ICompilationUnit> units, IProgressMonitor monitor) {
		var groups= units.stream().collect(Collectors.groupingBy(ICompilationUnit::getJavaProject,
				LinkedHashMap::new, Collectors.toList()));
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		for (var group : groups.entrySet()) {
			checkCanceled(monitor);
			roots.putAll(parse(group.getKey(), group.getValue(), monitor));
		}
		return roots;
	}

	private static Map<String, CompilationUnit> parse(IJavaProject project, List<ICompilationUnit> units,
			IProgressMonitor monitor) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(source.getPrimary().getHandleIdentifier(), ast);
			}
		}, monitor);
		return roots;
	}

	private static JUnitScopeCandidateDetector.SearchSeeds empty() {
		return new JUnitScopeCandidateDetector.SearchSeeds(false, true, List.of(), List.of());
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
