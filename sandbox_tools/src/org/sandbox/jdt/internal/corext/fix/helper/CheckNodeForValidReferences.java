package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Iterator;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.internal.corext.dom.AbortSearchException;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.sandbox.ast.api.expr.ASTExpr;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.jdt.FluentASTVisitor;
import org.sandbox.ast.api.jdt.JDTConverter;

class CheckNodeForValidReferences {
	private static final String ITERATOR_NAME= Iterator.class.getCanonicalName();

	private final ASTNode fASTNode;
	private final boolean fLocalVarsOnly;

	public CheckNodeForValidReferences(ASTNode node, boolean localVarsOnly) {
		fASTNode= node;
		fLocalVarsOnly= localVarsOnly;
	}

	public boolean isValid() {
		FluentASTVisitor visitor= new FluentASTVisitor() {

			@Override
			public boolean visit(FieldAccess visitedField) {
				if (visitedField.resolveFieldBinding() == null) {
					throw new AbortSearchException();
				}
				if (fLocalVarsOnly && visitedField.getLocationInParent() == MethodInvocation.EXPRESSION_PROPERTY) {
					MethodInvocation methodInvocation= ASTNodes.getParent(visitedField, MethodInvocation.class);
					MethodInvocationExpr miExpr= JDTConverter.convert(methodInvocation);
					if (miExpr.returnsType(ITERATOR_NAME)) {
						throw new AbortSearchException();
					}
				}
				return true;
			}

			@Override
			public boolean visit(SuperFieldAccess visitedField) {
				if (visitedField.resolveFieldBinding() == null) {
					throw new AbortSearchException();
				}
				if (fLocalVarsOnly && visitedField.getLocationInParent() == MethodInvocation.EXPRESSION_PROPERTY) {
					MethodInvocation methodInvocation= ASTNodes.getParent(visitedField, MethodInvocation.class);
					MethodInvocationExpr miExpr= JDTConverter.convert(methodInvocation);
					if (miExpr.returnsType(ITERATOR_NAME)) {
						throw new AbortSearchException();
					}
				}
				return true;
			}

			@Override
			protected boolean visitMethodInvocation(MethodInvocationExpr miExpr, MethodInvocation methodInvocation) {
				if (fLocalVarsOnly) {
					IMethodBinding methodInvocationBinding= methodInvocation.resolveMethodBinding();
					if (methodInvocationBinding == null) {
						throw new AbortSearchException();
					}
					ITypeBinding methodTypeBinding= methodInvocationBinding.getReturnType();
					if (AbstractTool.isOfType(methodTypeBinding, ITERATOR_NAME)) {
						Expression exp= methodInvocation.getExpression();
						if (exp instanceof SimpleName simpleName) {
							SimpleNameExpr nameExpr= JDTConverter.convert(simpleName);
							IBinding binding= simpleName.resolveBinding();
							if (binding instanceof IVariableBinding varBinding) {
								// Check using fluent API for field and parameter, but use JDT for record component
								if (nameExpr.resolveVariable()
										.filter(var -> !var.isField() && !var.isParameter())
										.filter(var -> var.hasType(ITERATOR_NAME))
										.isPresent() && !varBinding.isRecordComponent()) {
									return true;
								}
							}
						}
						throw new AbortSearchException();
					}
				}
				return true;
			}

			@Override
			public boolean visit(CastExpression castExpression) {
				Type castType= castExpression.getType();
				ITypeBinding typeBinding= castType.resolveBinding();
				if (AbstractTool.isOfType(typeBinding, ITERATOR_NAME)) {
					Expression exp= castExpression.getExpression();
					if (exp instanceof Name) {
						SimpleNameExpr nameExpr= JDTConverter.convertExpression(exp)
								.flatMap(ASTExpr::asSimpleName)
								.orElse(null);
						if (nameExpr != null && nameExpr.isVariable()) {
							if (!fLocalVarsOnly) {
								// For non-local mode, require field, parameter, or record component
								if (nameExpr.isLocalVariable()) {
									throw new AbortSearchException();
								}
							} else {
								// For local-only mode, reject field, parameter, or record component
								if (!nameExpr.isLocalVariable()) {
									throw new AbortSearchException();
								}
							}
							return true;
						}
					}
					throw new AbortSearchException();
				}
				return true;
			}

			@Override
			protected boolean visitSimpleName(SimpleNameExpr nameExpr, SimpleName simpleName) {
				if (!nameExpr.isVariable()) {
					return true;
				}
				
				return nameExpr.resolveVariable()
						.filter(var -> var.hasType(ITERATOR_NAME))
						.map(var -> {
							// Check if SimpleName is used as receiver for a method invocation
							if (simpleName.getLocationInParent() == MethodInvocation.EXPRESSION_PROPERTY) {
								MethodInvocation methodInvocation= ASTNodes.getParent(simpleName, MethodInvocation.class);
								MethodInvocationExpr miExpr= JDTConverter.convert(methodInvocation);
								if (!miExpr.returnsType(ITERATOR_NAME)) {
									return true;
								}
							}
							
							// Check variable kind based on fLocalVarsOnly flag
							if (!fLocalVarsOnly) {
								// For non-local mode, require field, parameter, or record component
								if (nameExpr.isLocalVariable()) {
									throw new AbortSearchException();
								}
							} else {
								// For local-only mode, reject field, parameter, or record component
								if (!nameExpr.isLocalVariable()) {
									throw new AbortSearchException();
								}
							}
							return true;
						})
						.orElse(true);
			}
		};
		try {
			fASTNode.accept(visitor);
			return true;
		} catch (AbortSearchException e) {
			// do nothing and fall through
		}
		return false;
	}

}