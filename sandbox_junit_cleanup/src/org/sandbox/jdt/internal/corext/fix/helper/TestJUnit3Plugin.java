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
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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
			} else if (("setUp".equals(name) || "tearDown".equals(name)) //$NON-NLS-1$ //$NON-NLS-2$
					&& !isLifecycleMethod(method, name)) {
				return false;
			}
		}
		return testFound;
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
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation invocation) {
				unsupported[0]|= !JUnit3LegacyShape.isRedundantLifecycleSuperCall(invocation);
				return false;
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				String name= invocation.getName().getIdentifier();
				IMethodBinding methodBinding= invocation.resolveMethodBinding();
				if (methodBinding == null) {
					unsupported[0]|= CUSTOM_EXECUTION_METHODS.contains(name);
					return !unsupported[0];
				}
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

		boolean removedSelfSuite= false;
		for (MethodDeclaration method : node.getMethods()) {
			if (JUnit3SuiteModel.isSuiteBuilder(method) && JUnit3LegacyShape.isSelfSuite(method, node)) {
				removedSelfSuite= true;
				break;
			}
		}
		if (removedSelfSuite) {
			// Remove the legacy simple-name import before addAnnotationToMethod()
			// adds org.junit.jupiter.api.Test. Reversing this order caused the
			// later removal to discard the newly added Jupiter import as well.
			importRewriter.removeImport(JUnit3SuiteModel.JUNIT3_TEST);
			importRewriter.removeImport(JUnit3SuiteModel.JUNIT3_TEST_SUITE);
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
			}
			if (method.getBody() != null) {
				removeRedundantLifecycleSuperCalls(method, rewriter, group);
				rewriteAssertions(method, rewriter, ast, group, importRewriter);
			}
		}
	}

	private void removeRedundantLifecycleSuperCalls(MethodDeclaration method, ASTRewrite rewriter,
			TextEditGroup group) {
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation invocation) {
				if (JUnit3LegacyShape.isRedundantLifecycleSuperCall(invocation)
						&& invocation.getParent() instanceof org.eclipse.jdt.core.dom.ExpressionStatement statement) {
					rewriter.remove(statement, group);
				}
				return false;
			}
		});
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
