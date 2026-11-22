/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3LegacyShape;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3MigrationExclusions;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SuiteModel;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;

/**
 * Migrates only a narrowly proven, self-contained JUnit 3 {@code TestCase} to
 * Jupiter. Hierarchy-driven or custom JUnit 3 execution models are deliberately
 * rejected until they can be migrated by a coordinated project-wide planner.
 */
public class TestJUnit3Plugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String JUNIT3_TEST_CASE= "junit.framework.TestCase"; //$NON-NLS-1$
	private static final String JUNIT3_ASSERT= "junit.framework.Assert"; //$NON-NLS-1$

	private static final String ORG_JUNIT_JUPITER_API_ORDER= "org.junit.jupiter.api.Order"; //$NON-NLS-1$
	private static final String ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER=
			"org.junit.jupiter.api.TestMethodOrder"; //$NON-NLS-1$
	private static final String ORG_JUNIT_JUPITER_API_ORDER_ANNOTATION=
			"org.junit.jupiter.api.MethodOrderer.OrderAnnotation"; //$NON-NLS-1$

	/** JUnit 3/JUnit 4 MethodSorter.DEFAULT for collision-free test names. */
	private static final Comparator<MethodDeclaration> JUNIT3_METHOD_ORDER=
			Comparator.comparingInt((MethodDeclaration method) -> method.getName().getIdentifier().hashCode())
					.thenComparing(method -> method.getName().getIdentifier());

	private static final Set<String> KNOWN_JUNIT3_ASSERTION_METHODS= Set.of(
			"assertEquals", "assertArrayEquals", "assertTrue", "assertFalse", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"assertNull", "assertNotNull", "assertSame", "assertNotSame", "fail"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	private static final Set<String> CUSTOM_EXECUTION_METHODS= Set.of(
			"suite", "runTest", "runBare", "createResult", "countTestCases", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"getName", "setName", "run"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		HelperVisitorFactory.callTypeDeclarationVisitor(JUNIT3_TEST_CASE, compilationUnit, dataHolder, nodesprocessed,
				(visited, holder) -> processFoundNode(fixcore, operations, visited, holder, nodesprocessed));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, TypeDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder, Set<ASTNode> nodesprocessed) {
		if (nodesprocessed.contains(node) || !isSafeStandaloneCandidate(node)) {
			return false;
		}
		nodesprocessed.add(node);
		JunitHolder holder= new JunitHolder();
		holder.setMinv(node);
		dataHolder.put(dataHolder.size(), holder);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	private boolean isSafeStandaloneCandidate(TypeDeclaration node) {
		ITypeBinding binding= node.resolveBinding();
		ITypeBinding superclass= binding == null ? null : binding.getSuperclass();
		if (binding == null || superclass == null || !node.isPackageMemberTypeDeclaration()
				|| !(node.getRoot() instanceof CompilationUnit root) || root.types().size() != 1
				|| !Modifier.isPublic(node.getModifiers()) || Modifier.isAbstract(node.getModifiers())
				|| !JUNIT3_TEST_CASE.equals(superclass.getErasure().getQualifiedName())
				|| hasJUnitAnnotation(node) || !(binding.getJavaElement() instanceof IType type)) {
			return false;
		}
		if (JUnit3MigrationExclusions.isExcluded(binding)
				|| hasKnownSubtypes(type) || hasUnsafeReferences(type) || hasUnsupportedTestCaseUsage(node)) {
			return false;
		}

		boolean testFound= false;
		for (MethodDeclaration method : node.getMethods()) {
			if (method.isConstructor()) {
				if (!JUnit3LegacyShape.isRemovableConstructor(method)) {
					return false;
				}
				continue;
			}
			if (hasJUnitAnnotation(method)) {
				return false;
			}
			if (JUnit3SuiteModel.isSuiteBuilder(method)) {
				if (!JUnit3LegacyShape.isSelfSuite(method, node)) {
					return false;
				}
				continue;
			}
			String name= method.getName().getIdentifier();
			if (CUSTOM_EXECUTION_METHODS.contains(name)) {
				return false;
			}
			if (name.startsWith("test")) { //$NON-NLS-1$
				if (!isTestMethod(method)) {
					return false;
				}
				testFound= true;
			} else if ("setUp".equals(name) || "tearDown".equals(name)) { //$NON-NLS-1$ //$NON-NLS-2$
				if (!isLifecycleMethod(method, name) || !hasSupportedLifecycleBody(method, binding)) {
					return false;
				}
			}
		}
		return testFound;
	}

	private static boolean hasSupportedLifecycleBody(MethodDeclaration method, ITypeBinding owner) {
		Block body= method.getBody();
		if (body == null || body.statements().isEmpty()) {
			return true;
		}
		if (owner == null || owner.isRecovered()) {
			return false;
		}
		// Removing the TestCase override can expose static-method or empty-block
		// diagnostics. Retain the class unless a real owning-instance use survives.
		boolean[] instanceUsed= { false };
		body.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				return !instanceUsed[0] && !(node instanceof AbstractTypeDeclaration)
						&& !(node instanceof AnonymousClassDeclaration);
			}

			@Override
			public boolean visit(SuperMethodInvocation invocation) {
				// The supported TestCase lifecycle calls are removed by the rewrite.
				return false;
			}

			@Override
			public boolean visit(ThisExpression expression) {
				ITypeBinding type= expression.resolveTypeBinding();
				instanceUsed[0]= type != null && !type.isRecovered() && owner.isEqualTo(type);
				return false;
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				IMethodBinding binding= invocation.resolveMethodBinding();
				ITypeBinding declaring= binding == null ? null : binding.getDeclaringClass();
				if (binding == null || binding.isRecovered() || declaring == null || declaring.isRecovered()) {
					return false;
				}
				if (JUNIT3_TEST_CASE.equals(declaring.getQualifiedName()) || JUNIT3_ASSERT.equals(declaring.getQualifiedName())) {
					// Assertion receivers are replaced, but their arguments survive.
					for (Object argument : invocation.arguments()) {
						((ASTNode) argument).accept(this);
					}
					return false;
				}
				instanceUsed[0]= invocation.getExpression() == null && !Modifier.isStatic(binding.getModifiers())
						&& isOwningInstanceMember(owner, declaring);
				return !instanceUsed[0];
			}

			@Override
			public boolean visit(SimpleName name) {
				if (!name.isDeclaration() && name.resolveBinding() instanceof IVariableBinding variable
						&& !variable.isRecovered() && variable.isField() && !Modifier.isStatic(variable.getModifiers())) {
					ASTNode parent= name.getParent();
					boolean selector= parent instanceof QualifiedName qualified && qualified.getName() == name
							|| parent instanceof FieldAccess field && field.getName() == name
							|| parent instanceof SuperFieldAccess superField && superField.getName() == name;
					instanceUsed[0]= !selector && isOwningInstanceMember(owner, variable.getDeclaringClass());
				}
				return false;
			}
		});
		return instanceUsed[0] && canRemoveLifecycleSuperCalls(method);
	}

	private static boolean isOwningInstanceMember(ITypeBinding owner, ITypeBinding declaring) {
		return declaring != null && !declaring.isRecovered()
				&& (owner.isEqualTo(declaring) || owner.isSubTypeCompatible(declaring));
	}

	private static boolean canRemoveLifecycleSuperCalls(MethodDeclaration method) {
		boolean[] safe= { true };
		method.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				return safe[0];
			}

			@Override
			public boolean visit(SuperMethodInvocation invocation) {
				if (!JUnit3LegacyShape.isRedundantLifecycleSuperCall(invocation)) {
					return false;
				}
				ASTNode statement= invocation.getParent();
				if (!(statement.getParent() instanceof Block block)) {
					// A required if/loop/label body cannot simply be removed.
					safe[0]= false;
					return false;
				}
				for (ASTNode child= statement, parent= child.getParent(); parent != null && parent != method;
						child= parent, parent= parent.getParent()) {
					if (parent instanceof TryStatement enclosing && child == enclosing.getBody()
							&& !enclosing.catchClauses().isEmpty()) {
						// The declared Exception on the removed call may keep a catch reachable.
						safe[0]= false;
						return false;
					}
				}
				if (!(block.getParent() instanceof TryStatement enclosing && enclosing.getFinally() == block)
						&& containsOnlyRedundantLifecycleCalls(block) && !containsRetainedComment(block)) {
					// Only finally blocks have a proven normalization in the rewrite below.
					safe[0]= false;
				}
				return false;
			}
		});
		return safe[0];
	}

	private static boolean containsOnlyRedundantLifecycleCalls(Block block) {
		for (Object value : block.statements()) {
			if (!(value instanceof ExpressionStatement statement
					&& statement.getExpression() instanceof SuperMethodInvocation invocation
					&& JUnit3LegacyShape.isRedundantLifecycleSuperCall(invocation))) {
				return false;
			}
		}
		return true;
	}

	private List<MethodDeclaration> orderedTestMethods(TypeDeclaration node) {
		return Arrays.stream(node.getMethods())
				.filter(this::isTestMethod)
				.sorted(JUNIT3_METHOD_ORDER)
				.toList();
	}

	private boolean hasKnownSubtypes(IType type) {
		try {
			ITypeHierarchy hierarchy= type.newTypeHierarchy(null);
			return hierarchy.getAllSubtypes(type).length != 0;
		} catch (JavaModelException e) {
			return true;
		}
	}

	private boolean hasUnsafeReferences(IType type) {
		SearchPattern pattern= SearchPattern.createPattern(type, IJavaSearchConstants.REFERENCES);
		if (pattern == null) {
			return true;
		}
		boolean[] unsafe= { false };
		ICompilationUnit declarationUnit= type.getCompilationUnit();
		try {
			new SearchEngine().search(pattern,
					new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(), new SearchRequestor() {
						@Override
						public void acceptSearchMatch(SearchMatch match) {
							if (unsafe[0]) {
								return;
							}
							if (match.getAccuracy() != SearchMatch.A_ACCURATE
									|| !(match.getElement() instanceof IJavaElement element)
									|| !(element.getAncestor(IJavaElement.COMPILATION_UNIT) instanceof ICompilationUnit unit)) {
								unsafe[0]= true;
								return;
							}
							if (declarationUnit != null && declarationUnit.equals(unit)) {
								return;
							}
							unsafe[0]= !isPureSuiteReference(unit, match.getOffset(), match.getLength());
						}
					}, null);
		} catch (CoreException e) {
			return true;
		}
		return unsafe[0];
	}

	private boolean isPureSuiteReference(ICompilationUnit unit, int offset, int length) {
		ASTParser parser= ASTParser.newParser(AST.JLS21);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		ASTNode node= NodeFinder.perform(root, offset, Math.max(0, length));
		MethodDeclaration method= null;
		TypeDeclaration owner= null;
		for (ASTNode current= node; current != null; current= current.getParent()) {
			if (method == null && current instanceof MethodDeclaration declaration) {
				method= declaration;
			}
			if (current instanceof TypeDeclaration declaration) {
				owner= declaration;
				break;
			}
		}
		return method != null && owner != null && JUnit3SuiteModel.isSuiteBuilder(method)
				&& JUnit3LegacyShape.isPureSuiteAggregator(owner, method);
	}

	private boolean hasUnsupportedTestCaseUsage(TypeDeclaration node) {
		boolean[] unsupported= { false };
		ITypeBinding owner= node.resolveBinding();
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(ThisExpression expression) {
				if (isOwnInstance(expression, owner)) {
					unsupported[0]|= requiresLegacyContract(ASTNodes.getTargetType(expression));
				}
				return !unsupported[0];
			}

			@Override
			public boolean visit(ExpressionMethodReference reference) {
				unsupported[0]|= isSelfSuiteReference(reference.resolveMethodBinding(), owner);
				return !unsupported[0];
			}

			@Override
			public boolean visit(TypeMethodReference reference) {
				unsupported[0]|= isSelfSuiteReference(reference.resolveMethodBinding(), owner);
				return !unsupported[0];
			}

			@Override
			public boolean visit(SuperMethodReference reference) {
				unsupported[0]= true;
				return false;
			}

			@Override
			public boolean visit(SuperMethodInvocation invocation) {
				unsupported[0]|= !JUnit3LegacyShape.isRedundantLifecycleSuperCall(invocation);
				return false;
			}

			@Override
			public boolean visit(ClassInstanceCreation creation) {
				unsupported[0]|= requiresLegacyArguments(creation.resolveConstructorBinding(), creation.arguments(), owner);
				return !unsupported[0];
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				String name= invocation.getName().getIdentifier();
				IMethodBinding methodBinding= invocation.resolveMethodBinding();
				if (methodBinding == null) {
					unsupported[0]|= CUSTOM_EXECUTION_METHODS.contains(name);
					return !unsupported[0];
				}
				unsupported[0]|= isSelfSuiteReference(methodBinding, owner);
				unsupported[0]|= requiresLegacyArguments(methodBinding, invocation.arguments(), owner);
				ITypeBinding declaringClass= methodBinding.getDeclaringClass();
				String declaringName= declaringClass == null ? "" //$NON-NLS-1$
						: declaringClass.getErasure().getQualifiedName();
				if ((JUNIT3_TEST_CASE.equals(declaringName) || JUNIT3_ASSERT.equals(declaringName))
						&& !KNOWN_JUNIT3_ASSERTION_METHODS.contains(name)) {
					unsupported[0]= true;
				}
				return !unsupported[0];
			}
		});
		return unsupported[0];
	}

	private static boolean requiresLegacyArguments(IMethodBinding method, List<?> arguments, ITypeBinding owner) {
		if (method == null) return false;
		// Inferred parameters can have the test's own type even though the method
		// or constructor declaration still requires T extends TestCase (or Test).
		ITypeBinding[] parameters= method.getMethodDeclaration().getParameterTypes();
		for (int index= 0; index < arguments.size() && parameters.length != 0; index++) {
			ITypeBinding parameter= parameters[Math.min(index, parameters.length - 1)];
			if (method.isVarargs() && index >= parameters.length - 1) {
				parameter= parameter.getComponentType();
			}
			if (isOwnInstance((Expression) arguments.get(index), owner) && requiresLegacyContract(parameter)) {
				return true;
			}
		}
		return false;
	}

	private static boolean requiresLegacyContract(ITypeBinding type) {
		return requiresLegacyContract(type, new HashSet<>());
	}

	private static boolean requiresLegacyContract(ITypeBinding type, Set<String> visited) {
		if (type == null || !visited.add(type.getKey())) return false;
		String name= type.getErasure().getQualifiedName();
		if (JUNIT3_TEST_CASE.equals(name) || JUNIT3_ASSERT.equals(name) || JUnit3SuiteModel.JUNIT3_TEST.equals(name)) {
			return true;
		}
		// Erasure retains only the first upper bound. A capture's lower bound is
		// available through its wildcard rather than getTypeBounds().
		return requiresLegacyContract(type.getBound(), visited)
				|| requiresLegacyContract(type.getWildcard(), visited)
				|| Arrays.stream(type.getTypeBounds()).anyMatch(bound -> requiresLegacyContract(bound, visited));
	}

	private static boolean isOwnInstance(Expression expression, ITypeBinding owner) {
		if (expression instanceof ParenthesizedExpression parenthesized) {
			return isOwnInstance(parenthesized.getExpression(), owner);
		}
		if (expression instanceof ConditionalExpression conditional) {
			return isOwnInstance(conditional.getThenExpression(), owner) || isOwnInstance(conditional.getElseExpression(), owner);
		}
		return expression instanceof ThisExpression && owner != null && owner.isEqualTo(expression.resolveTypeBinding());
	}

	private static boolean isSelfSuiteReference(IMethodBinding method, ITypeBinding owner) {
		return method != null && "suite".equals(method.getName()) && owner != null //$NON-NLS-1$
				&& owner.isEqualTo(method.getDeclaringClass());
	}

	private boolean hasJUnitAnnotation(BodyDeclaration declaration) {
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding annotationBinding= annotation.resolveTypeBinding();
				String name= annotationBinding == null
						? annotation.getTypeName().getFullyQualifiedName()
						: annotationBinding.getQualifiedName();
				if (name.startsWith("org.junit.") || name.startsWith("junit.framework.")) { //$NON-NLS-1$ //$NON-NLS-2$
					return true;
				}
			}
		}
		return false;
	}

	private boolean isTestMethod(MethodDeclaration method) {
		return !method.isConstructor() && method.getName().getIdentifier().startsWith("test") //$NON-NLS-1$
				&& Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
				&& method.parameters().isEmpty() && isVoidReturnType(method);
	}

	private boolean isLifecycleMethod(MethodDeclaration method, String expectedName) {
		return expectedName.equals(method.getName().getIdentifier())
				&& !Modifier.isStatic(method.getModifiers()) && !Modifier.isPrivate(method.getModifiers())
				&& method.parameters().isEmpty() && isVoidReturnType(method);
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		TypeDeclaration node= junitHolder.getTypeDeclaration();
		Type superclass= node.getSuperclassType();
		if (superclass != null) {
			rewriter.remove(superclass, group);
			importRewriter.removeImport(JUNIT3_TEST_CASE);
		}

		boolean removesSelfSuite= Arrays.stream(node.getMethods())
				.anyMatch(method -> JUnit3SuiteModel.isSuiteBuilder(method)
						&& JUnit3LegacyShape.isSelfSuite(method, node));
		if (removesSelfSuite) {
			// Remove the legacy simple-name imports before requesting Jupiter @Test.
			importRewriter.removeImport(JUnit3SuiteModel.JUNIT3_TEST);
			importRewriter.removeImport(JUnit3SuiteModel.JUNIT3_TEST_SUITE);
		}
		if (!needsLegacyFrameworkImport(node)) {
			importRewriter.removeImport("junit.framework.*"); //$NON-NLS-1$
		}

		List<MethodDeclaration> orderedTests= orderedTestMethods(node);
		if (orderedTests.size() > 1) {
			addTestMethodOrder(node, rewriter, ast, importRewriter, group);
		}
		for (MethodDeclaration method : node.getMethods()) {
			if (method.isConstructor() && JUnit3LegacyShape.isRemovableConstructor(method)) {
				rewriter.remove(method, group);
				continue;
			}
			if (JUnit3SuiteModel.isSuiteBuilder(method) && JUnit3LegacyShape.isSelfSuite(method, node)) {
				rewriter.remove(method, group);
				continue;
			}
			if (isLifecycleMethod(method, "setUp")) { //$NON-NLS-1$
				convertToAnnotation(method, "BeforeEach", importRewriter, rewriter, ast, group); //$NON-NLS-1$
			} else if (isLifecycleMethod(method, "tearDown")) { //$NON-NLS-1$
				convertToAnnotation(method, "AfterEach", importRewriter, rewriter, ast, group); //$NON-NLS-1$
			} else if (isTestMethod(method)) {
				addAnnotationToMethod(method, "Test", importRewriter, rewriter, ast, group); //$NON-NLS-1$
				if (orderedTests.size() > 1) {
					addOrderAnnotation(method, orderedTests.indexOf(method) + 1,
							importRewriter, rewriter, ast, group);
				}
			}
			if (method.getBody() != null) {
				removeRedundantLifecycleSuperCalls(method, rewriter, group);
				rewriteAssertions(method, rewriter, ast, group, importRewriter);
			}
		}
	}

	private static boolean needsLegacyFrameworkImport(TypeDeclaration owner) {
		CompilationUnit root= (CompilationUnit) owner.getRoot();
		List<SimpleName> references= new ArrayList<>();
		ImportReferencesCollector.collect(root, root.getJavaElement().getJavaProject(), null, references, null);
		for (SimpleName reference : references) {
			if (!(reference.resolveBinding() instanceof ITypeBinding type) || type.getPackage() == null
					|| !"junit.framework".equals(type.getPackage().getName())) { //$NON-NLS-1$
				continue;
			}
			boolean removed= false;
			for (ASTNode current= reference; current != null && current != owner; current= current.getParent()) {
				if (current == owner.getSuperclassType()
						|| current instanceof MethodDeclaration method && method.getParent() == owner
								&& (method.isConstructor() && JUnit3LegacyShape.isRemovableConstructor(method)
										|| JUnit3SuiteModel.isSuiteBuilder(method) && JUnit3LegacyShape.isSelfSuite(method, owner))) {
					removed= true;
					break;
				}
			}
			if (!removed) return true;
		}
		return false;
	}

	private void addTestMethodOrder(TypeDeclaration type, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		SingleMemberAnnotation annotation= ast.newSingleMemberAnnotation();
		annotation.setTypeName(ast.newSimpleName("TestMethodOrder")); //$NON-NLS-1$
		TypeLiteral orderer= ast.newTypeLiteral();
		orderer.setType(ast.newSimpleType(ast.newSimpleName("OrderAnnotation"))); //$NON-NLS-1$
		annotation.setValue(orderer);
		ListRewrite modifiers= rewrite.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
		modifiers.insertFirst(annotation, group);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_ORDER_ANNOTATION);
	}

	private void addOrderAnnotation(MethodDeclaration method, int order, ImportRewrite importRewrite,
			ASTRewrite rewrite, AST ast, TextEditGroup group) {
		SingleMemberAnnotation annotation= ast.newSingleMemberAnnotation();
		annotation.setTypeName(ast.newSimpleName("Order")); //$NON-NLS-1$
		NumberLiteral value= ast.newNumberLiteral(Integer.toString(order));
		annotation.setValue(value);
		ListRewrite modifiers= rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		modifiers.insertFirst(annotation, group);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_ORDER);
	}

	private void removeRedundantLifecycleSuperCalls(MethodDeclaration method, ASTRewrite rewriter,
			TextEditGroup group) {
		Set<Block> affectedFinalizers= new HashSet<>();
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation invocation) {
				if (JUnit3LegacyShape.isRedundantLifecycleSuperCall(invocation)
						&& invocation.getParent() instanceof org.eclipse.jdt.core.dom.ExpressionStatement statement) {
					if (rewriter.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer ranges) {
						ranges.addTightSourceNode(statement);
					}
					rewriter.remove(statement, group);
					if (statement.getParent() instanceof Block block
							&& block.getParent() instanceof TryStatement owner && owner.getFinally() == block) {
						affectedFinalizers.add(block);
					}
				}
				return false;
			}

			@Override
			public void endVisit(TryStatement statement) {
				Block finalizer= statement.getFinally();
				if (finalizer == null || !affectedFinalizers.contains(finalizer)
						|| !rewriter.getListRewrite(finalizer, Block.STATEMENTS_PROPERTY).getRewrittenList().isEmpty()
						|| containsRetainedComment(finalizer)) {
					return;
				}
				if (statement.resources().isEmpty() && statement.catchClauses().isEmpty()) {
					// Keep the body block: its locals must not leak into the enclosing scope.
					rewriter.replace(statement, rewriter.createMoveTarget(statement.getBody()), group);
				} else {
					rewriter.remove(finalizer, group);
				}
			}
		});
	}

	private static boolean containsRetainedComment(Block block) {
		if (block.getRoot() instanceof CompilationUnit root) {
			for (Object value : root.getCommentList()) {
				if (value instanceof Comment comment && comment.getStartPosition() > block.getStartPosition()
						&& comment.getStartPosition() < block.getStartPosition() + block.getLength()
						&& !isInsideRemovedStatement(comment, block)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isInsideRemovedStatement(Comment comment, Block block) {
		// The rewritten statement list is empty, so comments inside these ranges disappear too.
		for (Object value : block.statements()) {
			if (value instanceof ASTNode statement && comment.getStartPosition() >= statement.getStartPosition()
					&& comment.getStartPosition() + comment.getLength() <= statement.getStartPosition() + statement.getLength()) {
				return true;
			}
		}
		return false;
	}

	private void rewriteAssertions(MethodDeclaration method, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter) {
		ReferenceHolder<String, Object> holder= ReferenceHolder.create();
		AstProcessorBuilder.with(holder)
				.onMethodInvocation((node, ignored) -> {
					boolean junitAssertion= false;
					if (node.resolveMethodBinding() != null) {
						String owner= node.resolveMethodBinding().getDeclaringClass().getQualifiedName();
						junitAssertion= JUNIT3_ASSERT.equals(owner) || JUNIT3_TEST_CASE.equals(owner);
					} else if (node.getExpression() == null) {
						junitAssertion= KNOWN_JUNIT3_ASSERTION_METHODS.contains(node.getName().getIdentifier());
					}
					if (junitAssertion) {
						reorderParameters(node, rewriter, group, ONEPARAM_ASSERTIONS, TWOPARAM_ASSERTIONS);
						if (node.getExpression() != null) {
							rewriter.set(node.getExpression(), SimpleName.IDENTIFIER_PROPERTY, "Assertions", group); //$NON-NLS-1$
						} else {
							rewriter.set(node, MethodInvocation.EXPRESSION_PROPERTY,
									ast.newSimpleName("Assertions"), group); //$NON-NLS-1$
						}
						addImportForAssertion(node.getName().getIdentifier(), importRewriter);
					}
					return true;
				})
				.build(method);
	}

	private void addImportForAssertion(String assertionMethod, ImportRewrite importRewriter) {
		if (KNOWN_JUNIT3_ASSERTION_METHODS.contains(assertionMethod)) {
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSERTIONS);
		}
	}

	private boolean isVoidReturnType(MethodDeclaration method) {
		Type returnType= method.getReturnType2();
		return returnType != null && returnType.isPrimitiveType()
				&& PrimitiveType.VOID.equals(((org.eclipse.jdt.core.dom.PrimitiveType) returnType).getPrimitiveTypeCode());
	}

	private void convertToAnnotation(MethodDeclaration method, String annotation, ImportRewrite importRewrite,
			ASTRewrite rewrite, AST ast, TextEditGroup group) {
		removeOverrideAnnotation(method, rewrite, group);
		addAnnotationToMethod(method, annotation, importRewrite, rewrite, ast, group);
	}

	private void removeOverrideAnnotation(MethodDeclaration method, ASTRewrite rewrite, TextEditGroup group) {
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation annotation
					&& "Override".equals(annotation.getTypeName().getFullyQualifiedName())) { //$NON-NLS-1$
				rewrite.remove(annotation, group);
				return;
			}
		}
	}

	private void addAnnotationToMethod(MethodDeclaration method, String annotation, ImportRewrite importRewrite,
			ASTRewrite rewrite, AST ast, TextEditGroup group) {
		ListRewrite modifiers= rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		MarkerAnnotation newMarkerAnnotation= AnnotationUtils.createMarkerAnnotation(ast, annotation);
		modifiers.insertFirst(newMarkerAnnotation, group);
		importRewrite.addImport("org.junit.jupiter.api." + annotation); //$NON-NLS-1$
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		return afterRefactoring ? "import org.junit.jupiter.api.Test;\n" //$NON-NLS-1$
				: "import junit.framework.TestCase;\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "TestCase"; //$NON-NLS-1$
	}
}
