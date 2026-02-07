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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.sandbox.functional.core.tree.ScopeInfo;

/**
 * Lightweight scanner that analyzes a loop body to populate {@link ScopeInfo}.
 * 
 * <p>This scanner identifies:
 * <ul>
 * <li>Variables accessed from the outer scope</li>
 * <li>Variables modified within the loop (not effectively final)</li>
 * <li>Local variables declared in the loop</li>
 * </ul>
 * </p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * LoopBodyScopeScanner scanner = new LoopBodyScopeScanner(loopStatement);
 * scanner.scan();
 * ScopeInfo scopeInfo = scanner.populateScopeInfo(parentScopeInfo);
 * }</pre>
 * 
 * @since 1.0.0
 */
public class LoopBodyScopeScanner {
	private final EnhancedForStatement loop;
	private final String loopParameterName;
	private final Set<String> localVariables = new HashSet<>();
	private final Set<String> modifiedVariables = new HashSet<>();
	private final Set<String> referencedVariables = new HashSet<>();
	
	/**
	 * Creates a new scope scanner for the given loop.
	 * 
	 * @param loop the enhanced for statement to scan
	 */
	public LoopBodyScopeScanner(EnhancedForStatement loop) {
		this.loop = loop;
		this.loopParameterName = loop.getParameter().getName().getIdentifier();
	}
	
	/**
	 * Scans the loop body to collect variable information.
	 */
	public void scan() {
		Statement body = loop.getBody();
		if (body == null) {
			return;
		}
		
		// First pass: collect variable declarations
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment node) {
				String varName = node.getName().getIdentifier();
				localVariables.add(varName);
				return true;
			}
			
			@Override
			public boolean visit(SingleVariableDeclaration node) {
				String varName = node.getName().getIdentifier();
				localVariables.add(varName);
				return true;
			}
			
			@Override
			public boolean visit(EnhancedForStatement node) {
				// Don't descend into nested loops - they'll be analyzed separately
				return node == loop;
			}
			
			@Override
			public boolean visit(ForStatement node) {
				// Don't descend into nested loops
				return false;
			}
			
			@Override
			public boolean visit(WhileStatement node) {
				// Don't descend into nested loops
				return false;
			}
			
			@Override
			public boolean visit(DoStatement node) {
				// Don't descend into nested loops
				return false;
			}
		});
		
		// Second pass: collect variable references and modifications
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				String varName = node.getIdentifier();
				
				// Skip if this is a declaration
				if (node.isDeclaration()) {
					return true;
				}
				
				// Skip the loop parameter itself
				if (varName.equals(loopParameterName)) {
					return true;
				}
				
				// Skip local variables declared in this loop
				if (localVariables.contains(varName)) {
					return true;
				}
				
				// Try to resolve binding to filter out fields
				IBinding binding = node.resolveBinding();
				if (binding instanceof IVariableBinding) {
					IVariableBinding varBinding = (IVariableBinding) binding;
					
					// Skip fields - they're not in local scope
					if (varBinding.isField()) {
						return true;
					}
				}
				
				// This is a reference to a variable from outer scope
				referencedVariables.add(varName);
				
				// Check if this variable is being modified
				if (isModification(node)) {
					modifiedVariables.add(varName);
				}
				
				return true;
			}
			
			@Override
			public boolean visit(EnhancedForStatement node) {
				// Don't descend into nested loops - they'll be analyzed separately
				return node == loop;
			}
			
			@Override
			public boolean visit(ForStatement node) {
				// Don't descend into nested loops
				return false;
			}
			
			@Override
			public boolean visit(WhileStatement node) {
				// Don't descend into nested loops
				return false;
			}
			
			@Override
			public boolean visit(DoStatement node) {
				// Don't descend into nested loops
				return false;
			}
		});
	}
	
	/**
	 * Checks if a SimpleName node represents a modification of the variable.
	 * 
	 * <p>This method checks for direct modifications to the variable itself:
	 * <ul>
	 * <li>{@code x = 5} - MODIFICATION (x is directly the LHS)</li>
	 * <li>{@code x++} or {@code ++x} - MODIFICATION</li>
	 * <li>{@code arr[i] = 5} - NOT a modification of arr (only the element is modified)</li>
	 * <li>{@code obj.field = 5} - NOT a modification of obj (only the field is modified)</li>
	 * </ul>
	 * 
	 * <p>This is correct for lambda capture purposes: modifying array elements or
	 * object fields doesn't make the variable non-effectively-final. Only direct
	 * reassignment of the variable itself does.</p>
	 * 
	 * @param node the SimpleName node to check
	 * @return true if the variable itself is being modified
	 */
	private boolean isModification(SimpleName node) {
		// Check if node is the DIRECT left-hand side of an assignment
		// (not part of a complex LHS like arr[i] or obj.field)
		if (node.getParent() instanceof Assignment) {
			Assignment assignment = (Assignment) node.getParent();
			if (assignment.getLeftHandSide() == node) {
				return true;
			}
		}
		
		// Check if node is part of increment/decrement expression
		if (node.getParent() instanceof PostfixExpression) {
			PostfixExpression postfix = (PostfixExpression) node.getParent();
			PostfixExpression.Operator op = postfix.getOperator();
			if (op == PostfixExpression.Operator.INCREMENT || op == PostfixExpression.Operator.DECREMENT) {
				return true;
			}
		}
		
		if (node.getParent() instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression) node.getParent();
			PrefixExpression.Operator op = prefix.getOperator();
			if (op == PrefixExpression.Operator.INCREMENT || op == PrefixExpression.Operator.DECREMENT) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Populates the given ScopeInfo with the collected variable information.
	 * 
	 * @param scopeInfo the ScopeInfo to populate
	 */
	public void populateScopeInfo(ScopeInfo scopeInfo) {
		// Add local variables declared in this loop
		for (String varName : localVariables) {
			scopeInfo.addLocalVariable(varName);
		}
		
		// Add modified variables
		for (String varName : modifiedVariables) {
			scopeInfo.addModifiedVariable(varName);
		}
		
		// Note: outerScopeVariables are handled by ScopeInfo.createChildScope()
		// which already propagates parent scope variables. We don't need to
		// explicitly add them here, they're already in the ScopeInfo from the parent.
	}
	
	/**
	 * Gets the set of variables referenced from outer scope.
	 * 
	 * @return unmodifiable set of referenced variable names
	 */
	public Set<String> getReferencedVariables() {
		return Set.copyOf(referencedVariables);
	}
	
	/**
	 * Gets the set of variables modified in this loop.
	 * 
	 * @return unmodifiable set of modified variable names
	 */
	public Set<String> getModifiedVariables() {
		return Set.copyOf(modifiedVariables);
	}
	
	/**
	 * Gets the set of local variables declared in this loop.
	 * 
	 * @return unmodifiable set of local variable names
	 */
	public Set<String> getLocalVariables() {
		return Set.copyOf(localVariables);
	}
}
