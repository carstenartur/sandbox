/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Shared fail-closed semantic checks for the first method-reuse slice. */
final class MethodReuseSemanticSupport {

	record MethodDescriptor(String compilationUnitHandle, String sourceRootHandle, String packageName,
			String declaringTypeQualifiedName, String methodName, String methodBindingKey,
			String signatureKey, String fingerprint, boolean targetEligible) {
		String candidateId() {
			return declaringTypeQualifiedName + '#' + methodName + ':' + methodBindingKey;
		}

		String groupKey() {
			return sourceRootHandle + '\n' + packageName + '\n' + signatureKey + '\n' + fingerprint;
		}
	}

	private MethodReuseSemanticSupport() {
	}

	static MethodDescriptor describe(ICompilationUnit unit, CompilationUnit root, MethodDeclaration method) {
		if (unit == null || root == null || method == null || method.isConstructor()
				|| method.getBody() == null || !Modifier.isStatic(method.getModifiers())
				|| Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())
				|| Modifier.isSynchronized(method.getModifiers()) || method.isVarargs()
				|| !method.typeParameters().isEmpty() || !method.thrownExceptionTypes().isEmpty()
				|| method.parameters().isEmpty() || method.getBody().statements().size() != 1
				|| !(method.getBody().statements().get(0) instanceof ReturnStatement returned)
				|| returned.getExpression() == null || hasBodyComment(root, method)) {
			return null;
		}

		if (!(method.getParent() instanceof TypeDeclaration type) || !(type.getParent() instanceof CompilationUnit)
				|| type.isInterface() || !type.typeParameters().isEmpty()) {
			return null;
		}
		IMethodBinding methodBinding= method.resolveBinding();
		ITypeBinding typeBinding= type.resolveBinding();
		if (methodBinding == null || typeBinding == null || methodBinding.getReturnType() == null
				|| methodBinding.getReturnType().isPrimitive() && "void".equals(methodBinding.getReturnType().getName())) { //$NON-NLS-1$
			return null;
		}
		methodBinding= methodBinding.getMethodDeclaration();
		typeBinding= typeBinding.getTypeDeclaration();
		if (methodBinding.getKey() == null || typeBinding.getKey() == null
				|| typeBinding.getQualifiedName() == null || typeBinding.getQualifiedName().isBlank()) {
			return null;
		}

		Map<String, Integer> parameters= parameterIndexes(method);
		if (parameters == null || !safeExpression(returned.getExpression(), parameters, methodBinding)) {
			return null;
		}
		String signature= signatureKey(methodBinding);
		String fingerprint= fingerprint(returned.getExpression(), parameters);
		if (signature == null || fingerprint == null) {
			return null;
		}

		IJavaElement rootElement= unit.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (!(rootElement instanceof IPackageFragmentRoot sourceRoot)) {
			return null;
		}
		IPackageBinding packageBinding= typeBinding.getPackage();
		String packageName= packageBinding == null ? "" : packageBinding.getName(); //$NON-NLS-1$
		boolean targetEligible= !Modifier.isPrivate(method.getModifiers()) && safeTargetType(type);
		return new MethodDescriptor(unit.getPrimary().getHandleIdentifier(), sourceRoot.getHandleIdentifier(),
				packageName, typeBinding.getQualifiedName(), method.getName().getIdentifier(),
				methodBinding.getKey(), signature, fingerprint, targetEligible);
	}

	static String fingerprint(MethodDeclaration method) {
		if (method == null || method.getBody() == null || method.getBody().statements().size() != 1
				|| !(method.getBody().statements().get(0) instanceof ReturnStatement returned)
				|| returned.getExpression() == null) {
			return null;
		}
		Map<String, Integer> parameters= parameterIndexes(method);
		IMethodBinding binding= method.resolveBinding();
		if (parameters == null || binding == null
				|| !safeExpression(returned.getExpression(), parameters, binding.getMethodDeclaration())) {
			return null;
		}
		return fingerprint(returned.getExpression(), parameters);
	}

	static String signatureKey(MethodDeclaration method) {
		IMethodBinding binding= method == null ? null : method.resolveBinding();
		return binding == null ? null : signatureKey(binding.getMethodDeclaration());
	}

	static MethodDeclaration findMethod(CompilationUnit root, String bindingKey) {
		if (root == null || bindingKey == null) {
			return null;
		}
		MethodDeclaration[] result= new MethodDeclaration[1];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				IMethodBinding binding= method.resolveBinding();
				if (binding != null && bindingKey.equals(binding.getMethodDeclaration().getKey())) {
					if (result[0] != null) {
						result[0]= null;
						return false;
					}
					result[0]= method;
					return false;
				}
				return true;
			}
		});
		return result[0];
	}

	static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(unit);
		parser.setProject(unit.getJavaProject());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(unit.getJavaProject()));
		return (CompilationUnit) parser.createAST(null);
	}

	private static Map<String, Integer> parameterIndexes(MethodDeclaration method) {
		Map<String, Integer> result= new HashMap<>();
		for (int index= 0; index < method.parameters().size(); index++) {
			if (!(method.parameters().get(index) instanceof SingleVariableDeclaration parameter)) {
				return null;
			}
			IVariableBinding binding= parameter.resolveBinding();
			String key= binding == null ? null : binding.getVariableDeclaration().getKey();
			if (key == null || result.put(key, Integer.valueOf(index)) != null) {
				return null;
			}
		}
		return result;
	}

	private static String signatureKey(IMethodBinding binding) {
		if (binding == null || binding.getReturnType() == null || binding.getReturnType().getKey() == null) {
			return null;
		}
		StringBuilder result= new StringBuilder(binding.getReturnType().getKey());
		for (ITypeBinding parameterType : binding.getParameterTypes()) {
			if (parameterType == null || parameterType.getKey() == null) {
				return null;
			}
			appendToken(result, parameterType.getKey());
		}
		return result.toString();
	}

	private static boolean safeExpression(Expression expression, Map<String, Integer> parameters,
			IMethodBinding owner) {
		boolean[] safe= { true };
		expression.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (!safe[0]) {
					return;
				}
				switch (node.getNodeType()) {
					case ASTNode.ASSIGNMENT, ASTNode.POSTFIX_EXPRESSION, ASTNode.CLASS_INSTANCE_CREATION,
							ASTNode.ARRAY_CREATION, ASTNode.LAMBDA_EXPRESSION, ASTNode.CREATION_REFERENCE,
							ASTNode.EXPRESSION_METHOD_REFERENCE, ASTNode.SUPER_METHOD_REFERENCE,
							ASTNode.TYPE_METHOD_REFERENCE, ASTNode.SUPER_METHOD_INVOCATION,
							ASTNode.SUPER_FIELD_ACCESS, ASTNode.THIS_EXPRESSION, ASTNode.SWITCH_EXPRESSION -> safe[0]= false;
					default -> {
						if (node instanceof PrefixExpression prefix
								&& (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
										|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT)) {
							safe[0]= false;
						}
					}
				}
			}

			@Override
			public boolean visit(SimpleName name) {
				IBinding binding= name.resolveBinding();
				if (binding == null) {
					safe[0]= false;
					return false;
				}
				if (binding instanceof IVariableBinding variable) {
					String key= variable.getVariableDeclaration().getKey();
					if (key == null || !parameters.containsKey(key)) {
						safe[0]= false;
					}
				} else if (binding instanceof IMethodBinding method
						&& owner != null && owner.getKey().equals(method.getMethodDeclaration().getKey())) {
					safe[0]= false;
				}
				return safe[0];
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				if (invocation.resolveMethodBinding() == null) {
					safe[0]= false;
				}
				return safe[0];
			}
		});
		return safe[0];
	}

	private static String fingerprint(Expression expression, Map<String, Integer> parameters) {
		StringBuilder result= new StringBuilder(192);
		return appendNode(result, expression, parameters) ? result.toString() : null;
	}

	private static boolean appendNode(StringBuilder result, ASTNode node, Map<String, Integer> parameters) {
		appendToken(result, Integer.toString(node.getNodeType()));
		if (node instanceof SimpleName name) {
			IBinding binding= name.resolveBinding();
			String semantic= semanticBinding(binding, parameters);
			if (semantic == null) {
				return false;
			}
			appendToken(result, semantic);
			return true;
		}
		for (Object rawDescriptor : node.structuralPropertiesForType()) {
			StructuralPropertyDescriptor descriptor= (StructuralPropertyDescriptor) rawDescriptor;
			appendToken(result, descriptor.getId());
			Object value= node.getStructuralProperty(descriptor);
			if (descriptor.isChildProperty()) {
				if (value == null) {
					appendToken(result, "null"); //$NON-NLS-1$
				} else if (!appendNode(result, (ASTNode) value, parameters)) {
					return false;
				}
			} else if (descriptor.isChildListProperty()) {
				List<?> children= (List<?>) value;
				appendToken(result, Integer.toString(children.size()));
				for (Object child : children) {
					if (!appendNode(result, (ASTNode) child, parameters)) {
						return false;
					}
				}
			} else {
				appendToken(result, String.valueOf(value));
			}
		}
		return true;
	}

	private static String semanticBinding(IBinding binding, Map<String, Integer> parameters) {
		if (binding == null) {
			return null;
		}
		if (binding instanceof IVariableBinding variable) {
			Integer index= parameters.get(variable.getVariableDeclaration().getKey());
			return index == null ? null : "$" + index; //$NON-NLS-1$
		}
		if (binding instanceof IMethodBinding method) {
			return "M:" + method.getMethodDeclaration().getKey(); //$NON-NLS-1$
		}
		if (binding instanceof ITypeBinding type) {
			return "T:" + type.getTypeDeclaration().getKey(); //$NON-NLS-1$
		}
		if (binding instanceof IPackageBinding packageBinding) {
			return "P:" + packageBinding.getName(); //$NON-NLS-1$
		}
		return binding.getKey() == null ? null : binding.getKind() + ":" + binding.getKey(); //$NON-NLS-1$
	}

	private static boolean safeTargetType(TypeDeclaration type) {
		for (FieldDeclaration field : type.getFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				return false;
			}
		}
		for (Object declaration : type.bodyDeclarations()) {
			if (declaration instanceof Initializer initializer && Modifier.isStatic(initializer.getModifiers())) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasBodyComment(CompilationUnit root, MethodDeclaration method) {
		int start= method.getBody().getStartPosition();
		int end= start + method.getBody().getLength();
		for (Object rawComment : root.getCommentList()) {
			Comment comment= (Comment) rawComment;
			if (comment.getStartPosition() >= start && comment.getStartPosition() < end) {
				return true;
			}
		}
		return false;
	}

	private static void appendToken(StringBuilder result, String value) {
		String normalized= value == null ? "null" : value; //$NON-NLS-1$
		result.append(normalized.length()).append(':').append(normalized).append(';');
	}
}
