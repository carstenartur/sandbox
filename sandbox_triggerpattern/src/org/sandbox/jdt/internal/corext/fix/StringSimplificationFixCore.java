/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Fix core for string simplification using TriggerPattern hints.
 * 
 * <p>This class applies string simplification patterns as cleanup operations,
 * transforming patterns like {@code "" + x} and {@code x + ""} to {@code String.valueOf(x)}.</p>
 * 
 * @since 1.2.2
 */
public class StringSimplificationFixCore {
	
	private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();
	
	/**
	 * Finds string simplification operations in the compilation unit.
	 * 
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			java.util.Set<CompilationUnitRewriteOperation> operations) {
		
		// Pattern 1: "" + $x
		Pattern emptyPrefixPattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> emptyPrefixMatches = ENGINE.findMatches(compilationUnit, emptyPrefixPattern);
		for (Match match : emptyPrefixMatches) {
			operations.add(new StringValueOfOperation(match, "Empty string prefix")); //$NON-NLS-1$
		}
		
		// Pattern 2: $x + ""
		Pattern emptySuffixPattern = new Pattern("$x + \"\"", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> emptySuffixMatches = ENGINE.findMatches(compilationUnit, emptySuffixPattern);
		for (Match match : emptySuffixMatches) {
			operations.add(new StringValueOfOperation(match, "Empty string suffix")); //$NON-NLS-1$
		}
		
		// Pattern 3: $str.length() == 0
		Pattern lengthCheckPattern = new Pattern("$str.length() == 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> lengthCheckMatches = ENGINE.findMatches(compilationUnit, lengthCheckPattern);
		for (Match match : lengthCheckMatches) {
			operations.add(new IsEmptyOperation(match, "String length check")); //$NON-NLS-1$
		}
		
		// Pattern 4: $str.equals("")
		Pattern equalsEmptyPattern = new Pattern("$str.equals(\"\")", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> equalsEmptyMatches = ENGINE.findMatches(compilationUnit, equalsEmptyPattern);
		for (Match match : equalsEmptyMatches) {
			operations.add(new IsEmptyOperation(match, "String equals empty")); //$NON-NLS-1$
		}
		
		// Pattern 5: $x == true
		Pattern boolTruePattern = new Pattern("$x == true", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> boolTrueMatches = ENGINE.findMatches(compilationUnit, boolTruePattern);
		for (Match match : boolTrueMatches) {
			operations.add(new SimplifyBooleanOperation(match, "Boolean == true", false)); //$NON-NLS-1$
		}
		
		// Pattern 6: $x == false
		Pattern boolFalsePattern = new Pattern("$x == false", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> boolFalseMatches = ENGINE.findMatches(compilationUnit, boolFalsePattern);
		for (Match match : boolFalseMatches) {
			operations.add(new SimplifyBooleanOperation(match, "Boolean == false", true)); //$NON-NLS-1$
		}
		
		// Pattern 7: $cond ? true : false
		Pattern ternaryTrueFalsePattern = new Pattern("$cond ? true : false", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> ternaryTrueFalseMatches = ENGINE.findMatches(compilationUnit, ternaryTrueFalsePattern);
		for (Match match : ternaryTrueFalseMatches) {
			operations.add(new SimplifyTernaryOperation(match, "Ternary true:false", false)); //$NON-NLS-1$
		}
		
		// Pattern 8: $cond ? false : true
		Pattern ternaryFalseTruePattern = new Pattern("$cond ? false : true", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> ternaryFalseTrueMatches = ENGINE.findMatches(compilationUnit, ternaryFalseTruePattern);
		for (Match match : ternaryFalseTrueMatches) {
			operations.add(new SimplifyTernaryOperation(match, "Ternary false:true", true)); //$NON-NLS-1$
		}
		
		// Pattern 9: $list.size() == 0
		Pattern collectionSizePattern = new Pattern("$list.size() == 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> collectionSizeMatches = ENGINE.findMatches(compilationUnit, collectionSizePattern);
		for (Match match : collectionSizeMatches) {
			operations.add(new CollectionIsEmptyOperation(match, "Collection size check", "$list", false)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		// Pattern 10: $list.size() > 0
		Pattern collectionNotEmptyPattern = new Pattern("$list.size() > 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> collectionNotEmptyMatches = ENGINE.findMatches(compilationUnit, collectionNotEmptyPattern);
		for (Match match : collectionNotEmptyMatches) {
			operations.add(new CollectionIsEmptyOperation(match, "Collection non-empty check", "$list", true)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		// Pattern 11: new StringBuilder().append($x).toString()
		Pattern stringBuilderPattern = new Pattern("new StringBuilder().append($x).toString()", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> stringBuilderMatches = ENGINE.findMatches(compilationUnit, stringBuilderPattern);
		for (Match match : stringBuilderMatches) {
			operations.add(new MethodToStringValueOfOperation(match, "StringBuilder single append")); //$NON-NLS-1$
		}
		
		// Pattern 12: String.format("%s", $x)
		Pattern stringFormatPattern = new Pattern("String.format(\"%s\", $x)", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> stringFormatMatches = ENGINE.findMatches(compilationUnit, stringFormatPattern);
		for (Match match : stringFormatMatches) {
			operations.add(new MethodToStringValueOfOperation(match, "Redundant String.format")); //$NON-NLS-1$
		}
		
		// Pattern 13: $x.toString().equals($y)
		Pattern toStringEqualsPattern = new Pattern("$x.toString().equals($y)", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> toStringEqualsMatches = ENGINE.findMatches(compilationUnit, toStringEqualsPattern);
		for (Match match : toStringEqualsMatches) {
			operations.add(new ObjectsEqualsOperation(match, "Null-safe toString equals")); //$NON-NLS-1$
		}
		
		// Pattern 14: $x != null ? $x : $default
		Pattern nullCheckTernaryPattern = new Pattern("$x != null ? $x : $default", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> nullCheckTernaryMatches = ENGINE.findMatches(compilationUnit, nullCheckTernaryPattern);
		for (Match match : nullCheckTernaryMatches) {
			operations.add(new RequireNonNullElseOperation(match, "Null-check ternary")); //$NON-NLS-1$
		}
	}
	
	/**
	 * Rewrite operation for String.valueOf() simplification.
	 */
	private static class StringValueOfOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		
		public StringValueOfOperation(Match match, String description) {
			this.match = match;
			this.description = description;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof InfixExpression)) {
				return;
			}
			
			InfixExpression infixExpr = (InfixExpression) matchedNode;
			
			// Get the bound variable from placeholders
			ASTNode xNode = match.getBinding("$x"); //$NON-NLS-1$
			if (xNode == null || !(xNode instanceof Expression)) {
				return;
			}
			
			Expression valueExpression = (Expression) xNode;
			
			// Create the replacement: String.valueOf(valueExpression)
			MethodInvocation methodInvocation = ast.newMethodInvocation();
			methodInvocation.setExpression(ast.newName("String")); //$NON-NLS-1$
			methodInvocation.setName(ast.newSimpleName("valueOf")); //$NON-NLS-1$
			methodInvocation.arguments().add(ASTNode.copySubtree(ast, valueExpression));
			
			// Apply the rewrite
			rewrite.replace(infixExpr, methodInvocation, group);
		}
	}
	
	/**
	 * Rewrite operation for String.valueOf() simplification when the matched node is a MethodInvocation.
	 * 
	 * <p>Used for patterns like {@code new StringBuilder().append($x).toString()}
	 * and {@code String.format("%s", $x)} which match MethodInvocation nodes.</p>
	 */
	private static class MethodToStringValueOfOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		
		public MethodToStringValueOfOperation(Match match, String description) {
			this.match = match;
			this.description = description;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof MethodInvocation)) {
				return;
			}
			
			// Get the bound variable from placeholders
			ASTNode xNode = match.getBinding("$x"); //$NON-NLS-1$
			if (xNode == null || !(xNode instanceof Expression)) {
				return;
			}
			
			Expression valueExpression = (Expression) xNode;
			
			// Create the replacement: String.valueOf(valueExpression)
			MethodInvocation methodInvocation = ast.newMethodInvocation();
			methodInvocation.setExpression(ast.newName("String")); //$NON-NLS-1$
			methodInvocation.setName(ast.newSimpleName("valueOf")); //$NON-NLS-1$
			methodInvocation.arguments().add(ASTNode.copySubtree(ast, valueExpression));
			
			// Apply the rewrite
			rewrite.replace(matchedNode, methodInvocation, group);
		}
	}
	
	/**
	 * Rewrite operation for isEmpty() simplification.
	 */
	private static class IsEmptyOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		
		public IsEmptyOperation(Match match, String description) {
			this.match = match;
			this.description = description;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			
			// Get the bound variable from placeholders
			ASTNode strNode = match.getBinding("$str"); //$NON-NLS-1$
			if (strNode == null || !(strNode instanceof Expression)) {
				return;
			}
			
			Expression strExpression = (Expression) strNode;
			
			// Create the replacement: strExpression.isEmpty()
			MethodInvocation isEmptyCall = ast.newMethodInvocation();
			isEmptyCall.setExpression((Expression) ASTNode.copySubtree(ast, strExpression));
			isEmptyCall.setName(ast.newSimpleName("isEmpty")); //$NON-NLS-1$
			
			// Apply the rewrite
			rewrite.replace(matchedNode, isEmptyCall, group);
		}
	}
	
	/**
	 * Rewrite operation for boolean simplification.
	 */
	private static class SimplifyBooleanOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		private final boolean negate;
		
		public SimplifyBooleanOperation(Match match, String description, boolean negate) {
			this.match = match;
			this.description = description;
			this.negate = negate;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof InfixExpression)) {
				return;
			}
			
			InfixExpression infixExpr = (InfixExpression) matchedNode;
			
			// Get the boolean variable
			ASTNode xNode = match.getBinding("$x"); //$NON-NLS-1$
			if (xNode == null || !(xNode instanceof Expression)) {
				return;
			}
			
			Expression boolExpression = (Expression) xNode;
			
			Expression replacement;
			if (negate) {
				// Create: !boolExpression
				PrefixExpression negation = ast.newPrefixExpression();
				negation.setOperator(PrefixExpression.Operator.NOT);
				negation.setOperand((Expression) ASTNode.copySubtree(ast, boolExpression));
				replacement = negation;
			} else {
				// Just use the boolean expression as-is
				replacement = (Expression) ASTNode.copySubtree(ast, boolExpression);
			}
			
			// Apply the rewrite
			rewrite.replace(infixExpr, replacement, group);
		}
	}
	
	/**
	 * Rewrite operation for ternary boolean simplification.
	 */
	private static class SimplifyTernaryOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		private final boolean negate;
		
		public SimplifyTernaryOperation(Match match, String description, boolean negate) {
			this.match = match;
			this.description = description;
			this.negate = negate;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof ConditionalExpression)) {
				return;
			}
			
			ConditionalExpression ternary = (ConditionalExpression) matchedNode;
			
			// Get the condition
			ASTNode condNode = match.getBinding("$cond"); //$NON-NLS-1$
			if (condNode == null || !(condNode instanceof Expression)) {
				return;
			}
			
			Expression condition = (Expression) condNode;
			
			Expression replacement;
			if (negate) {
				// Create: !condition
				PrefixExpression negation = ast.newPrefixExpression();
				negation.setOperator(PrefixExpression.Operator.NOT);
				negation.setOperand((Expression) ASTNode.copySubtree(ast, condition));
				replacement = negation;
			} else {
				// Just use the condition as-is
				replacement = (Expression) ASTNode.copySubtree(ast, condition);
			}
			
			// Apply the rewrite
			rewrite.replace(ternary, replacement, group);
		}
	}
	
	/**
	 * Rewrite operation for collection isEmpty() simplification.
	 * 
	 * <p>Handles both {@code $list.size() == 0} → {@code $list.isEmpty()}
	 * and {@code $list.size() > 0} → {@code !$list.isEmpty()}.</p>
	 */
	private static class CollectionIsEmptyOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		private final String bindingName;
		private final boolean negate;
		
		public CollectionIsEmptyOperation(Match match, String description, String bindingName, boolean negate) {
			this.match = match;
			this.description = description;
			this.bindingName = bindingName;
			this.negate = negate;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			
			ASTNode listNode = match.getBinding(bindingName);
			if (listNode == null || !(listNode instanceof Expression)) {
				return;
			}
			
			Expression listExpression = (Expression) listNode;
			
			// Create: listExpression.isEmpty()
			MethodInvocation isEmptyCall = ast.newMethodInvocation();
			isEmptyCall.setExpression((Expression) ASTNode.copySubtree(ast, listExpression));
			isEmptyCall.setName(ast.newSimpleName("isEmpty")); //$NON-NLS-1$
			
			Expression replacement;
			if (negate) {
				// Create: !listExpression.isEmpty()
				PrefixExpression negation = ast.newPrefixExpression();
				negation.setOperator(PrefixExpression.Operator.NOT);
				negation.setOperand(isEmptyCall);
				replacement = negation;
			} else {
				replacement = isEmptyCall;
			}
			
			rewrite.replace(matchedNode, replacement, group);
		}
	}
	
	/**
	 * Rewrite operation for Objects.equals() null-safe transformation.
	 * 
	 * <p>Replaces {@code $x.toString().equals($y)} with {@code Objects.equals($x.toString(), $y)}.</p>
	 */
	private static class ObjectsEqualsOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		
		public ObjectsEqualsOperation(Match match, String description) {
			this.match = match;
			this.description = description;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof MethodInvocation)) {
				return;
			}
			
			ASTNode xNode = match.getBinding("$x"); //$NON-NLS-1$
			ASTNode yNode = match.getBinding("$y"); //$NON-NLS-1$
			
			if (xNode == null || !(xNode instanceof Expression) ||
			    yNode == null || !(yNode instanceof Expression)) {
				return;
			}
			
			Expression xExpression = (Expression) xNode;
			Expression yExpression = (Expression) yNode;
			
			// Add import for java.util.Objects
			ImportRewrite importRewrite = cuRewrite.getImportRewrite();
			String objectsType = importRewrite.addImport("java.util.Objects"); //$NON-NLS-1$
			
			// Create: Objects.equals(x.toString(), y)
			MethodInvocation equalsCall = ast.newMethodInvocation();
			equalsCall.setExpression(ast.newName(objectsType));
			equalsCall.setName(ast.newSimpleName("equals")); //$NON-NLS-1$
			
			// Create x.toString()
			MethodInvocation toStringCall = ast.newMethodInvocation();
			toStringCall.setExpression((Expression) ASTNode.copySubtree(ast, xExpression));
			toStringCall.setName(ast.newSimpleName("toString")); //$NON-NLS-1$
			
			equalsCall.arguments().add(toStringCall);
			equalsCall.arguments().add(ASTNode.copySubtree(ast, yExpression));
			
			rewrite.replace(matchedNode, equalsCall, group);
		}
	}
	
	/**
	 * Rewrite operation for Objects.requireNonNullElse() transformation.
	 * 
	 * <p>Replaces {@code $x != null ? $x : $default} with {@code Objects.requireNonNullElse($x, $default)}.</p>
	 */
	private static class RequireNonNullElseOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		
		public RequireNonNullElseOperation(Match match, String description) {
			this.match = match;
			this.description = description;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof ConditionalExpression)) {
				return;
			}
			
			ASTNode xNode = match.getBinding("$x"); //$NON-NLS-1$
			ASTNode defaultNode = match.getBinding("$default"); //$NON-NLS-1$
			
			if (xNode == null || !(xNode instanceof Expression) ||
			    defaultNode == null || !(defaultNode instanceof Expression)) {
				return;
			}
			
			Expression xExpression = (Expression) xNode;
			Expression defaultExpression = (Expression) defaultNode;
			
			// Add import for java.util.Objects
			ImportRewrite importRewrite = cuRewrite.getImportRewrite();
			String objectsType = importRewrite.addImport("java.util.Objects"); //$NON-NLS-1$
			
			// Create: Objects.requireNonNullElse(x, default)
			MethodInvocation requireNonNullElseCall = ast.newMethodInvocation();
			requireNonNullElseCall.setExpression(ast.newName(objectsType));
			requireNonNullElseCall.setName(ast.newSimpleName("requireNonNullElse")); //$NON-NLS-1$
			requireNonNullElseCall.arguments().add(ASTNode.copySubtree(ast, xExpression));
			requireNonNullElseCall.arguments().add(ASTNode.copySubtree(ast, defaultExpression));
			
			rewrite.replace(matchedNode, requireNonNullElseCall, group);
		}
	}
}
