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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ONEPARAM_ASSERTIONS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_ASSERTIONS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.TWOPARAM_ASSERTIONS;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;

/**
 * Migrates the smallest provably detachable Eclipse JDT Core JUnit 3 harness
 * leaf: one exact named constructor, one recognized discovery-only suite method
 * and one ordinary test method that uses no custom harness API.
 *
 * <p>The recognized suite forms are the inherited
 * {@code buildTestSuite(This.class)} call and the package-named wrapper used by
 * real compiler tests such as {@code IrritantSetTest}. This intentionally does
 * not support {@code SuiteOfTestCases}, inherited tests, configurable ordering,
 * filters, performance callbacks or lifecycle overrides. Those shapes remain
 * on Vintage until a dedicated harness model is available.</p>
 */
final class JdtCoreLeafJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String MODE= "JDT_CORE_DETACHABLE_LEAF"; //$NON-NLS-1$
	private static final String JDT_CORE_TEST_CASE=
			"org.eclipse.jdt.core.tests.junit.extension.TestCase"; //$NON-NLS-1$
	private static final String JUNIT3_ASSERT= "junit.framework.Assert"; //$NON-NLS-1$
	private static final String JUNIT3_TEST= "junit.framework.Test"; //$NON-NLS-1$
	private static final String JUNIT3_TEST_CASE= "junit.framework.TestCase"; //$NON-NLS-1$
	private static final String JUNIT3_TEST_SUITE= "junit.framework.TestSuite"; //$NON-NLS-1$
	private static final Set<String> ASSERTIONS= Set.of(
			"assertEquals", "assertArrayEquals", "assertTrue", "assertFalse", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"assertNull", "assertNotNull", "assertSame", "assertNotSame", "fail"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		HelperVisitorFactory.callTypeDeclarationVisitor(JDT_CORE_TEST_CASE, compilationUnit, dataHolder, nodesprocessed,
				(visited, holder) -> processFoundNode(fixcore, operations, visited, holder, nodesprocessed));
	}

	boolean handles(JunitHolder holder) {
		return MODE.equals(holder.getAdditionalInfo());
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, TypeDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder, Set<ASTNode> nodesprocessed) {
		if (nodesprocessed.contains(node) || !isSafeCandidate(node)) {
			return false;
		}
		nodesprocessed.add(node);
		JunitHolder holder= new JunitHolder().setMinv(node).setAdditionalInfo(MODE);
		dataHolder.put(dataHolder.size(), holder);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	private boolean isSafeCandidate(TypeDeclaration node) {
		ITypeBinding binding= node.resolveBinding();
		ITypeBinding superclass= binding == null ? null : binding.getSuperclass();
		if (binding == null || superclass == null || !node.isPackageMemberTypeDeclaration()
				|| !Modifier.isPublic(node.getModifiers()) || Modifier.isAbstract(node.getModifiers())
				|| !JDT_CORE_TEST_CASE.equals(superclass.getErasure().getQualifiedName())
				|| hasJUnitAnnotation(node) || !hasSingleTopLevelType(node)
				|| !(binding.getJavaElement() instanceof IType type)) {
			return false;
		}
		if (hasKnownSubtypes(type) || hasReferencesOutsideOwnCompilationUnit(type)) {
			return false;
		}

		MethodDeclaration constructor= null;
		MethodDeclaration suite= null;
		MethodDeclaration test= null;
		for (MethodDeclaration method : node.getMethods()) {
			if (hasJUnitAnnotation(method)) {
				return false;
			}
			if (method.isConstructor()) {
				if (constructor != null || !isTrivialNamedConstructor(method)) {
					return false;
				}
				constructor= method;
				continue;
			}
			String name= method.getName().getIdentifier();
			if ("suite".equals(name)) { //$NON-NLS-1$
				if (suite != null || !isDiscoveryOnlySuite(method, binding)) {
					return false;
				}
				suite= method;
				continue;
			}
			if ("setUp".equals(name) || "tearDown".equals(name) || "runTest".equals(name) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					|| "runBare".equals(name) || "getName".equals(name) || "setName".equals(name) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					|| "setUpTest".equals(name)) { //$NON-NLS-1$
				return false;
			}
			if (name.startsWith("test")) { //$NON-NLS-1$
				if (test != null || !isExactTestMethod(method)) {
					return false;
				}
				test= method;
			}
		}
		return constructor != null && suite != null && test != null
				&& !hasUnsupportedHarnessUsage(node, binding, constructor, suite);
	}

	private boolean isTrivialNamedConstructor(MethodDeclaration method) {
		if (!Modifier.isPublic(method.getModifiers()) || method.parameters().size() != 1
				|| method.getBody() == null || method.getBody().statements().size() != 1
				|| !method.thrownExceptionTypes().isEmpty() || !method.typeParameters().isEmpty()) {
			return false;
		}
		SingleVariableDeclaration parameter= (SingleVariableDeclaration) method.parameters().get(0);
		ITypeBinding parameterType= parameter.getType().resolveBinding();
		if (parameterType == null || !"java.lang.String".equals(parameterType.getErasure().getQualifiedName()) //$NON-NLS-1$
				|| parameter.isVarargs()) {
			return false;
		}
		Object statement= method.getBody().statements().get(0);
		if (!(statement instanceof SuperConstructorInvocation invocation) || invocation.arguments().size() != 1) {
			return false;
		}
		Object argument= invocation.arguments().get(0);
		return argument instanceof SimpleName name
				&& sameVariable(name.resolveBinding(), parameter.resolveBinding());
	}

	private boolean isDiscoveryOnlySuite(MethodDeclaration method, ITypeBinding testType) {
		if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
				|| !method.parameters().isEmpty() || method.getBody() == null
				|| !method.thrownExceptionTypes().isEmpty()) {
			return false;
		}
		ITypeBinding returnType= method.getReturnType2() == null ? null : method.getReturnType2().resolveBinding();
		if (returnType == null || !JUNIT3_TEST.equals(returnType.getErasure().getQualifiedName())) {
			return false;
		}
		return isBuildTestSuiteReturn(method, testType) || isPackageNamedTestSuite(method, testType);
	}

	private boolean isBuildTestSuiteReturn(MethodDeclaration method, ITypeBinding testType) {
		if (method.getBody().statements().size() != 1) {
			return false;
		}
		Object statement= method.getBody().statements().get(0);
		if (!(statement instanceof ReturnStatement returnStatement)
				|| !(returnStatement.getExpression() instanceof MethodInvocation invocation)
				|| invocation.getExpression() != null || !"buildTestSuite".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| invocation.arguments().size() != 1) {
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		ITypeBinding owner= binding == null ? null : binding.getMethodDeclaration().getDeclaringClass();
		return owner != null && JDT_CORE_TEST_CASE.equals(owner.getErasure().getQualifiedName())
				&& Modifier.isStatic(binding.getModifiers())
				&& isClassLiteral(invocation.arguments().get(0), testType);
	}

	private boolean isPackageNamedTestSuite(MethodDeclaration method, ITypeBinding testType) {
		if (method.getBody().statements().size() != 3
				|| !(method.getBody().statements().get(0) instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1
				|| !(declaration.fragments().get(0) instanceof VariableDeclarationFragment fragment)
				|| !(fragment.getInitializer() instanceof ClassInstanceCreation outerSuite)
				|| !isTestSuiteConstruction(outerSuite) || outerSuite.arguments().size() != 1
				|| !isPackageNameCall(outerSuite.arguments().get(0), testType)) {
			return false;
		}
		IVariableBinding suiteVariable= fragment.resolveBinding();
		ITypeBinding declaredType= declaration.getType().resolveBinding();
		if (suiteVariable == null || declaredType == null
				|| !JUNIT3_TEST_SUITE.equals(declaredType.getErasure().getQualifiedName())) {
			return false;
		}

		if (!(method.getBody().statements().get(1) instanceof ExpressionStatement expressionStatement)
				|| !(expressionStatement.getExpression() instanceof MethodInvocation addTest)
				|| !"addTest".equals(addTest.getName().getIdentifier()) || addTest.arguments().size() != 1 //$NON-NLS-1$
				|| !(addTest.getExpression() instanceof SimpleName suiteReference)
				|| !sameVariable(suiteReference.resolveBinding(), suiteVariable)
				|| !(addTest.arguments().get(0) instanceof ClassInstanceCreation innerSuite)
				|| !isTestSuiteConstruction(innerSuite) || innerSuite.arguments().size() != 1
				|| !isClassLiteral(innerSuite.arguments().get(0), testType)) {
			return false;
		}
		IMethodBinding addTestBinding= addTest.resolveMethodBinding();
		ITypeBinding addTestOwner= addTestBinding == null ? null
				: addTestBinding.getMethodDeclaration().getDeclaringClass();
		if (addTestOwner == null || !JUNIT3_TEST_SUITE.equals(addTestOwner.getErasure().getQualifiedName())) {
			return false;
		}

		Object last= method.getBody().statements().get(2);
		return last instanceof ReturnStatement returnStatement
				&& returnStatement.getExpression() instanceof SimpleName returnedSuite
				&& sameVariable(returnedSuite.resolveBinding(), suiteVariable);
	}

	private static boolean isTestSuiteConstruction(ClassInstanceCreation creation) {
		ITypeBinding type= creation.resolveTypeBinding();
		return type != null && JUNIT3_TEST_SUITE.equals(type.getErasure().getQualifiedName());
	}

	private static boolean isPackageNameCall(Object expression, ITypeBinding testType) {
		if (!(expression instanceof MethodInvocation invocation)
				|| !"getPackageName".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| !invocation.arguments().isEmpty()
				|| !(invocation.getExpression() instanceof TypeLiteral literal)
				|| !isClassLiteral(literal, testType)) {
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		ITypeBinding owner= binding == null ? null : binding.getMethodDeclaration().getDeclaringClass();
		return owner != null && "java.lang.Class".equals(owner.getErasure().getQualifiedName()); //$NON-NLS-1$
	}

	private static boolean isClassLiteral(Object expression, ITypeBinding expectedType) {
		if (!(expression instanceof TypeLiteral literal)) {
			return false;
		}
		ITypeBinding literalType= literal.getType().resolveBinding();
		return literalType != null && expectedType.getErasure().isEqualTo(literalType.getErasure());
	}

	private boolean hasUnsupportedHarnessUsage(TypeDeclaration node, ITypeBinding candidate,
			MethodDeclaration constructor, MethodDeclaration suite) {
		boolean[] unsupported= { false };
		node.accept(new ASTVisitor() {
			private MethodDeclaration current;

			@Override
			public boolean visit(MethodDeclaration method) {
				current= method;
				return method != constructor && method != suite;
			}

			@Override
			public void endVisit(MethodDeclaration method) {
				if (current == method) {
					current= null;
				}
			}

			@Override
			public boolean visit(SuperMethodInvocation invocation) {
				unsupported[0]= true;
				return false;
			}

			@Override
			public boolean visit(SuperFieldAccess access) {
				unsupported[0]= true;
				return false;
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				if (current == null) {
					return true;
				}
				IMethodBinding method= invocation.resolveMethodBinding();
				if (method == null) {
					unsupported[0]= true;
					return false;
				}
				ITypeBinding owner= method.getMethodDeclaration().getDeclaringClass();
				String ownerName= owner == null ? "" : owner.getErasure().getQualifiedName(); //$NON-NLS-1$
				String name= invocation.getName().getIdentifier();
				if ((JUNIT3_ASSERT.equals(ownerName) || JUNIT3_TEST_CASE.equals(ownerName))
						&& ASSERTIONS.contains(name)) {
					return true;
				}
				if (owner != null && !candidate.getErasure().isEqualTo(owner.getErasure())
						&& isSubtypeOf(owner, JUNIT3_TEST_CASE)) {
					unsupported[0]= true;
					return false;
				}
				return true;
			}

			@Override
			public boolean visit(SimpleName name) {
				if (current == null) {
					return true;
				}
				IBinding binding= name.resolveBinding();
				if (binding instanceof IVariableBinding variable && variable.isField()) {
					ITypeBinding owner= variable.getDeclaringClass();
					if (owner != null && !candidate.getErasure().isEqualTo(owner.getErasure())
							&& isSubtypeOf(owner, JUNIT3_TEST_CASE)) {
						unsupported[0]= true;
						return false;
					}
				}
				return true;
			}
		});
		return unsupported[0];
	}

	private boolean hasKnownSubtypes(IType type) {
		try {
			ITypeHierarchy hierarchy= type.newTypeHierarchy(null);
			return hierarchy.getAllSubtypes(type).length != 0;
		} catch (JavaModelException e) {
			return true;
		}
	}

	private boolean hasReferencesOutsideOwnCompilationUnit(IType type) {
		SearchPattern pattern= SearchPattern.createPattern(type, IJavaSearchConstants.REFERENCES);
		ICompilationUnit ownUnit= type.getCompilationUnit();
		if (pattern == null || ownUnit == null) {
			return true;
		}
		boolean[] referenced= { false };
		try {
			new SearchEngine().search(pattern,
					new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(), new SearchRequestor() {
						@Override
						public void acceptSearchMatch(SearchMatch match) {
							Object element= match.getElement();
							if (element instanceof IJavaElement javaElement) {
								IJavaElement ancestor= javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
								if (ancestor instanceof ICompilationUnit unit
										&& ownUnit.getPrimary().equals(unit.getPrimary())) {
									return;
								}
							}
							referenced[0]= true;
						}
					}, null);
		} catch (CoreException e) {
			return true;
		}
		return referenced[0];
	}

	private static boolean isSubtypeOf(ITypeBinding type, String expected) {
		ITypeBinding current= type;
		while (current != null) {
			if (expected.equals(current.getErasure().getQualifiedName())) {
				return true;
			}
			current= current.getSuperclass();
		}
		return false;
	}

	private static boolean sameVariable(IBinding first, IBinding second) {
		return first instanceof IVariableBinding left && second instanceof IVariableBinding right
				&& left.getVariableDeclaration().isEqualTo(right.getVariableDeclaration());
	}

	private static boolean hasSingleTopLevelType(TypeDeclaration node) {
		return node.getRoot() instanceof CompilationUnit root && root.types().size() == 1;
	}

	private static boolean hasJUnitAnnotation(BodyDeclaration declaration) {
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding annotationType= annotation.resolveTypeBinding();
				String name= annotationType == null ? annotation.getTypeName().getFullyQualifiedName()
						: annotationType.getQualifiedName();
				if (name.startsWith("org.junit.") || name.startsWith("junit.framework.")) { //$NON-NLS-1$ //$NON-NLS-2$
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isExactTestMethod(MethodDeclaration method) {
		Type returnType= method.getReturnType2();
		return !method.isConstructor() && method.getName().getIdentifier().startsWith("test") //$NON-NLS-1$
				&& Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
				&& method.parameters().isEmpty() && returnType != null && returnType.isPrimitiveType()
				&& PrimitiveType.VOID.equals(((org.eclipse.jdt.core.dom.PrimitiveType) returnType).getPrimitiveTypeCode());
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder holder) {
		TypeDeclaration type= holder.getTypeDeclaration();
		Type superclass= type.getSuperclassType();
		if (superclass != null) {
			rewriter.remove(superclass, group);
		}
		importRewriter.removeImport(JDT_CORE_TEST_CASE);
		importRewriter.removeImport(JUNIT3_TEST);
		importRewriter.removeImport(JUNIT3_TEST_SUITE);

		for (MethodDeclaration method : type.getMethods()) {
			if (method.isConstructor() || "suite".equals(method.getName().getIdentifier())) { //$NON-NLS-1$
				rewriter.remove(method, group);
				continue;
			}
			if (isExactTestMethod(method)) {
				ListRewrite modifiers= rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
				MarkerAnnotation annotation= AnnotationUtils.createMarkerAnnotation(ast, "Test"); //$NON-NLS-1$
				modifiers.insertFirst(annotation, group);
				importRewriter.addImport("org.junit.jupiter.api.Test"); //$NON-NLS-1$
				rewriteAssertions(method, rewriter, ast, group, importRewriter);
			}
		}
	}

	private void rewriteAssertions(MethodDeclaration method, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter) {
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				IMethodBinding binding= invocation.resolveMethodBinding();
				ITypeBinding owner= binding == null ? null : binding.getMethodDeclaration().getDeclaringClass();
				String ownerName= owner == null ? "" : owner.getErasure().getQualifiedName(); //$NON-NLS-1$
				String name= invocation.getName().getIdentifier();
				if ((JUNIT3_ASSERT.equals(ownerName) || JUNIT3_TEST_CASE.equals(ownerName))
						&& ASSERTIONS.contains(name)) {
					reorderParameters(invocation, rewriter, group, ONEPARAM_ASSERTIONS, TWOPARAM_ASSERTIONS);
					rewriter.set(invocation, MethodInvocation.EXPRESSION_PROPERTY,
							ast.newSimpleName("Assertions"), group); //$NON-NLS-1$
					importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSERTIONS);
				}
				return true;
			}
		});
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		return afterRefactoring ? "import org.junit.jupiter.api.Test;\n" //$NON-NLS-1$
				: "import org.eclipse.jdt.core.tests.junit.extension.TestCase;\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "JDT Core detachable TestCase leaf"; //$NON-NLS-1$
	}
}
