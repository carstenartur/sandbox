/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.SimplifyPlatformStatusFixCore;
import org.sandbox.jdt.internal.corext.util.ImportUtils;

/**
 * Shared implementation for semantics-preserving simplification of
 * {@link Status} constructor calls.
 *
 * <p>A factory is preferred when the explicit identity is a side-effect-free
 * value that is provably equal to the identity inferred for the calling class,
 * and the factory return type fits the surrounding source context. Otherwise
 * only a compile-time {@link IStatus#OK} code is removed and every explicit
 * constructor value is retained.</p>
 */
public abstract class AbstractSimplifyPlatformStatus {

	private static final String BUNDLE_SYMBOLIC_NAME= "Bundle-SymbolicName"; //$NON-NLS-1$
	private static final String FRAGMENT_HOST= "Fragment-Host"; //$NON-NLS-1$
	private static final String INFO_FACTORY_METHOD= "info"; //$NON-NLS-1$

	private final int expectedSeverity;
	private final String factoryMethodName;

	protected AbstractSimplifyPlatformStatus(int expectedSeverity) {
		this(expectedSeverity, null);
	}

	protected AbstractSimplifyPlatformStatus(int expectedSeverity, String factoryMethodName) {
		this.expectedSeverity= expectedSeverity;
		this.factoryMethodName= factoryMethodName;
	}

	/** Adds an import and returns a usable name for a generated type reference. */
	protected static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		return ImportUtils.addImport(typeName, cuRewrite.getImportRewrite(), ast);
	}

	/** Returns the compile-time integer value of an expression, or {@code null}. */
	protected static Integer constantIntValue(Expression expression) {
		Object value= expression.resolveConstantExpressionValue();
		return value instanceof Integer integer ? integer : null;
	}

	/** Tests an expression by compile-time value instead of source spelling. */
	protected static boolean hasConstantIntValue(Expression expression, int expected) {
		Integer value= constantIntValue(expression);
		return value != null && value.intValue() == expected;
	}

	public abstract String getPreview(boolean afterRefactoring);

	/** Finds exact {@link Status} constructors whose code is provably OK. */
	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		try {
			String bundleId= runtimeBundleSymbolicName(compilationUnit);
			ReferenceHolder<ASTNode, Object> holder= ReferenceHolder.createForNodes();
			HelperVisitorFactory.forClassInstanceCreation(Status.class)
				.in(compilationUnit)
				.excluding(nodesProcessed)
				.processEach(holder, (visited, data) -> {
					if (nodesProcessed.contains(visited) || visited.arguments().size() != 5) {
						return false;
					}

					ITypeBinding typeBinding= visited.resolveTypeBinding();
					if (typeBinding == null
							|| !Status.class.getName().equals(typeBinding.getErasure().getQualifiedName())) {
						return false;
					}

					List<Expression> arguments= visited.arguments();
					if (!hasConstantIntValue(arguments.get(0), expectedSeverity)
							|| !hasConstantIntValue(arguments.get(2), IStatus.OK)) {
						return false;
					}

					Integer factoryArgumentCount= factoryArgumentCount(visited, arguments, bundleId);
					operations.add(fixcore.rewrite(visited, data, factoryArgumentCount));
					nodesProcessed.add(visited);
					return false;
				});
		} catch (Exception exception) {
			throw new CoreException(Status.error("Problem while finding Status simplifications", exception)); //$NON-NLS-1$
		}
	}

	private Integer factoryArgumentCount(ClassInstanceCreation visited, List<Expression> arguments,
			String bundleId) {
		if (factoryMethodName == null || bundleId == null
				|| !hasEquivalentIdentity(arguments.get(1), visited, bundleId)) {
			return null;
		}
		boolean nullThrowable= ASTNodes.getUnparenthesedExpression(arguments.get(4)) instanceof NullLiteral;
		if (nullThrowable && hasCompatibleFactory(visited, 1)) {
			return Integer.valueOf(1);
		}
		return hasCompatibleFactory(visited, 2) ? Integer.valueOf(2) : null;
	}

	private boolean hasCompatibleFactory(ClassInstanceCreation visited, int argumentCount) {
		if (argumentCount == 2 && INFO_FACTORY_METHOD.equals(factoryMethodName)) {
			return false;
		}
		ITypeBinding statusType= visited.resolveTypeBinding();
		if (statusType == null) {
			return false;
		}
		ITypeBinding targetType= targetType(visited);
		for (IMethodBinding method : statusType.getErasure().getDeclaredMethods()) {
			ITypeBinding[] parameters= method.getParameterTypes();
			if (!factoryMethodName.equals(method.getName()) || !Modifier.isStatic(method.getModifiers())
					|| parameters.length != argumentCount
					|| !String.class.getName().equals(parameters[0].getErasure().getQualifiedName())
					|| argumentCount == 2
							&& !Throwable.class.getName().equals(parameters[1].getErasure().getQualifiedName())) {
				continue;
			}
			ITypeBinding returnType= method.getReturnType();
			if (Status.class.getName().equals(returnType.getErasure().getQualifiedName())
					|| targetType != null && returnType.isAssignmentCompatible(targetType)
					|| targetType == null && isExpressionStatement(visited)) {
				return true;
			}
		}
		return false;
	}

	private static ITypeBinding targetType(Expression expression) {
		ASTNode current= expression;
		ASTNode parent= current.getParent();
		while (parent instanceof ParenthesizedExpression) {
			current= parent;
			parent= parent.getParent();
		}
		if (parent instanceof VariableDeclarationFragment fragment && fragment.getInitializer() == current) {
			IVariableBinding variableBinding= fragment.resolveBinding();
			if (variableBinding != null) {
				return variableBinding.getType();
			}
		}
		return ASTNodes.getTargetType((Expression) current);
	}

	private static boolean hasEquivalentIdentity(Expression identity, ClassInstanceCreation visited,
			String bundleId) {
		Expression expression= ASTNodes.getUnparenthesedExpression(identity);
		Object constantValue= expression.resolveConstantExpressionValue();
		if (constantValue instanceof String stringValue) {
			return bundleId != null && bundleId.equals(stringValue);
		}
		if (!(expression instanceof TypeLiteral typeLiteral)) {
			return false;
		}
		ITypeBinding explicitType= typeLiteral.getType().resolveBinding();
		ITypeBinding callerType= enclosingTypeBinding(visited);
		return explicitType != null && callerType != null
				&& explicitType.getErasure().isEqualTo(callerType.getErasure());
	}

	private static ITypeBinding enclosingTypeBinding(ASTNode node) {
		for (ASTNode current= node.getParent(); current != null; current= current.getParent()) {
			if (current instanceof AnonymousClassDeclaration anonymousClass) {
				return anonymousClass.resolveBinding();
			}
			if (current instanceof AbstractTypeDeclaration typeDeclaration) {
				return typeDeclaration.resolveBinding();
			}
		}
		return null;
	}

	private static boolean isExpressionStatement(Expression expression) {
		ASTNode parent= expression.getParent();
		while (parent instanceof ParenthesizedExpression) {
			parent= parent.getParent();
		}
		return parent instanceof ExpressionStatement;
	}

	private static String runtimeBundleSymbolicName(CompilationUnit compilationUnit) {
		IJavaElement javaElement= compilationUnit.getJavaElement();
		if (javaElement == null) {
			return null;
		}
		IFile manifestFile= javaElement.getJavaProject().getProject().getFile("META-INF/MANIFEST.MF"); //$NON-NLS-1$
		if (!manifestFile.isAccessible()) {
			return null;
		}
		try (InputStream contents= manifestFile.getContents()) {
			Attributes attributes= new Manifest(contents).getMainAttributes();
			String fragmentHost= attributes.getValue(FRAGMENT_HOST);
			String runtimeIdentity= fragmentHost != null ? fragmentHost : attributes.getValue(BUNDLE_SYMBOLIC_NAME);
			if (runtimeIdentity == null) {
				return null;
			}
			int directiveStart= runtimeIdentity.indexOf(';');
			return (directiveStart < 0 ? runtimeIdentity : runtimeIdentity.substring(0, directiveStart)).trim();
		} catch (CoreException | IOException exception) {
			return null;
		}
	}

	/** Removes only the redundant OK code and retains every other argument. */
	public void rewrite(SimplifyPlatformStatusFixCore cleanup, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> holder) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRemover remover= cuRewrite.getImportRemover();

		ClassInstanceCreation simplifiedStatus= ast.newClassInstanceCreation();
		Name statusName= addImport(Status.class.getName(), cuRewrite, ast);
		simplifiedStatus.setType(ast.newSimpleType(statusName));

		List<Expression> originalArguments= visited.arguments();
		List<Expression> simplifiedArguments= simplifiedStatus.arguments();
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(0))));
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(1))));
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(3))));
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(4))));

		ASTNodes.replaceButKeepComment(rewrite, visited, simplifiedStatus, group);
		remover.registerRemovedNode(visited);
	}

	/** Uses a factory after identity and target-type equivalence have been proven. */
	public void rewrite(SimplifyPlatformStatusFixCore cleanup, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> holder, int factoryArgumentCount) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRemover remover= cuRewrite.getImportRemover();

		MethodInvocation factoryCall= ast.newMethodInvocation();
		factoryCall.setExpression(addImport(Status.class.getName(), cuRewrite, ast));
		factoryCall.setName(ast.newSimpleName(factoryMethodName));
		List<Expression> originalArguments= visited.arguments();
		List<Expression> factoryArguments= factoryCall.arguments();
		factoryArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(3))));
		if (factoryArgumentCount == 2) {
			factoryArguments.add(ASTNodes.createMoveTarget(rewrite,
					ASTNodes.getUnparenthesedExpression(originalArguments.get(4))));
		}

		ASTNodes.replaceButKeepComment(rewrite, visited, factoryCall, group);
		remover.registerRemovedNode(visited);
	}
}
