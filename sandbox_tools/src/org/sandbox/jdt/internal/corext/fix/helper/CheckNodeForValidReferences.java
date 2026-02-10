package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Iterator;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
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
import org.sandbox.ast.api.jdt.JDTConverter;
import org.sandbox.ast.api.expr.SimpleNameExpr;

class CheckNodeForValidReferences {
	private static final String ITERATOR_NAME= Iterator.class.getCanonicalName();

	private final ASTNode fASTNode;
	private final boolean fLocalVarsOnly;

	public CheckNodeForValidReferences(ASTNode node, boolean localVarsOnly) {
		fASTNode= node;
		fLocalVarsOnly= localVarsOnly;
	}

	public boolean isValid() {
		ASTVisitor visitor= new ASTVisitor() {

			@Override
			public boolean visit(FieldAccess visitedField) {
				IVariableBinding binding= visitedField.resolveFieldBinding();
				if (binding == null) {
					throw new AbortSearchException();
				}
				if (fLocalVarsOnly && visitedField.getLocationInParent() == MethodInvocation.EXPRESSION_PROPERTY) {
					MethodInvocation methodInvocation= ASTNodes.getParent(visitedField, MethodInvocation.class);
					IMethodBinding methodInvocationBinding= methodInvocation.resolveMethodBinding();
					if (methodInvocationBinding == null) {
						throw new AbortSearchException();
					}
					ITypeBinding methodTypeBinding= methodInvocationBinding.getReturnType();
					if (AbstractTool.isOfType(methodTypeBinding, ITERATOR_NAME)) {
						throw new AbortSearchException();
					}
				}
				return true;
			}

			@Override
			public boolean visit(SuperFieldAccess visitedField) {
				IVariableBinding binding= visitedField.resolveFieldBinding();
				if (binding == null) {
					throw new AbortSearchException();
				}
				if (fLocalVarsOnly && visitedField.getLocationInParent() == MethodInvocation.EXPRESSION_PROPERTY) {
					MethodInvocation methodInvocation= ASTNodes.getParent(visitedField, MethodInvocation.class);
					IMethodBinding methodInvocationBinding= methodInvocation.resolveMethodBinding();
					if (methodInvocationBinding == null) {
						throw new AbortSearchException();
					}
					ITypeBinding methodTypeBinding= methodInvocationBinding.getReturnType();
					if (AbstractTool.isOfType(methodTypeBinding, ITERATOR_NAME)) {
						throw new AbortSearchException();
					}
				}
				return true;
			}

			@Override
			public boolean visit(MethodInvocation methodInvocation) {
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
							if (nameExpr.resolveVariable()
									.filter(var -> !var.isField() && !var.isParameter())
									.filter(var -> var.hasType(ITERATOR_NAME))
									.isPresent()) {
								return true;
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
					if (exp instanceof Name name) {
						IBinding binding= name.resolveBinding();
						if (binding instanceof IVariableBinding simpleNameVarBinding) {
							if (!fLocalVarsOnly) {
								if (!simpleNameVarBinding.isField() && !simpleNameVarBinding.isParameter()
										&& !simpleNameVarBinding.isRecordComponent()) {
									throw new AbortSearchException();
								}
							} else {
								if (simpleNameVarBinding.isField() || simpleNameVarBinding.isParameter()
										|| simpleNameVarBinding.isRecordComponent()) {
									throw new AbortSearchException();
								}
							}
						}
					}
					throw new AbortSearchException();
				}
				return true;
			}

			@Override
			public boolean visit(SimpleName simpleName) {
				IBinding simpleNameBinding= simpleName.resolveBinding();
				if (simpleNameBinding == null) {
					throw new AbortSearchException();
				}
				if (!(simpleNameBinding instanceof IVariableBinding)) {
					return true;
				}
				
				SimpleNameExpr nameExpr= JDTConverter.convert(simpleName);
				return nameExpr.resolveVariable()
						.filter(var -> var.hasType(ITERATOR_NAME))
						.map(var -> {
							// Check if SimpleName is used as receiver for a method invocation
							if (simpleName.getLocationInParent() == MethodInvocation.EXPRESSION_PROPERTY) {
								MethodInvocation methodInvocation= ASTNodes.getParent(simpleName, MethodInvocation.class);
								IMethodBinding methodInvocationBinding= methodInvocation.resolveMethodBinding();
								if (methodInvocationBinding == null) {
									throw new AbortSearchException();
								}
								ITypeBinding methodInvocationReturnType= methodInvocationBinding.getReturnType();
								if (!AbstractTool.isOfType(methodInvocationReturnType, ITERATOR_NAME)) {
									return true;
								}
							}
							
							// Need to check record component via JDT binding (not yet in fluent API)
							IVariableBinding simpleNameVarBinding= (IVariableBinding) simpleNameBinding;
							
							// Check variable kind based on fLocalVarsOnly flag
							if (!fLocalVarsOnly) {
								// For non-local mode, require field, parameter, or record component
								if (!var.isField() && !var.isParameter() && !simpleNameVarBinding.isRecordComponent()) {
									throw new AbortSearchException();
								}
							} else {
								// For local-only mode, reject field, parameter, or record component
								if (var.isField() || var.isParameter() || simpleNameVarBinding.isRecordComponent()) {
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