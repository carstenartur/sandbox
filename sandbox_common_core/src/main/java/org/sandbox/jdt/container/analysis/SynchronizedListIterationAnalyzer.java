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
package org.sandbox.jdt.container.analysis;

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;

/** Reports compound membership checks, traversal and method-return boundaries for synchronized list fields. */
public final class SynchronizedListIterationAnalyzer {

	private static final String LIST= "java.util.List"; //$NON-NLS-1$

	/** Returns whole-field assessments of atomic copy-and-clear drains and their producers. */
	public List<ConcurrencyProtocolAssessment> assessDrains(CompilationUnit unit) {
		return SynchronizedListDrainAnalyzer.analyze(unit);
	}

	/** Returns source-ordered observations for matched conditional insertions, loops and non-private method returns. */
	public List<DiagnosticOnly> analyze(CompilationUnit unit) {
		List<DiagnosticOnly> observations= new ArrayList<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(IfStatement branch) {
				observeConditionalAdd(unit, branch).ifPresent(observations::add);
				return true;
			}

			@Override
			public boolean visit(EnhancedForStatement loop) {
				observe(unit, loop).ifPresent(observations::add);
				return true;
			}

			@Override
			public boolean visit(ReturnStatement statement) {
				observeReturn(unit, statement).ifPresent(observations::add);
				return true;
			}
		});
		return List.copyOf(observations);
	}

	/** Match the check and write separately; the wrapper synchronizes each method call. */
	private static Optional<DiagnosticOnly> observeConditionalAdd(CompilationUnit unit, IfStatement branch) {
		if (!(unwrap(branch.getExpression()) instanceof PrefixExpression negated)
				|| negated.getOperator() != PrefixExpression.Operator.NOT
				|| !(unwrap(negated.getOperand()) instanceof MethodInvocation contains)
				|| !isListOperation(contains, "contains")) { //$NON-NLS-1$
			return Optional.empty();
		}
		Statement then= branch.getThenStatement();
		if (then instanceof Block block && block.statements().size() == 1) {
			then= (Statement) block.statements().getFirst();
		}
		if (!(then instanceof ExpressionStatement statement)
				|| !(statement.getExpression() instanceof MethodInvocation add) || !isListOperation(add, "add") //$NON-NLS-1$
				|| !(unwrap((Expression) contains.arguments().getFirst()) instanceof SimpleName element)
				|| !(element.resolveBinding() instanceof IVariableBinding variable) || variable.isField() || variable.isRecovered()
				|| !(unwrap((Expression) add.arguments().getFirst()) instanceof SimpleName added)
				|| !variable.isEqualTo(added.resolveBinding())) {
			return Optional.empty();
		}
		VariableDeclarationFragment field= synchronizedListField(unit, contains.getExpression());
		if (field == null || !field.resolveBinding().isEqualTo(currentInstanceField(add.getExpression()))) {
			return Optional.empty();
		}
		IVariableBinding binding= field.resolveBinding().getVariableDeclaration();
		boolean locked= hasMatchingMonitor(branch, binding);
		String lock= locked ? binding.getKey() : null;
		String reason= locked
				? "The same wrapper monitor covers contains and add. A replacement must preserve that compound operation." //$NON-NLS-1$
				: "No enclosing monitor on this wrapper was verified across contains and add. Check whether callers serialize the operation."; //$NON-NLS-1$
		ContainerIdentity identity= new ContainerIdentity(binding.getKey(), binding.getName(),
				field.getName().getStartPosition(), field.getName().getLength());
		ConcurrencyProfile summary= new ConcurrencyProfile(ThreadExposure.UNKNOWN, SynchronizationKind.SYNCHRONIZED_WRAPPER,
				IterationSemantics.UNKNOWN, AtomicityRequirement.CHECK_THEN_ACT, WorkloadShape.UNKNOWN);
		List<Evidence> evidence= List.of(
				new Evidence(locked ? EvidenceKind.CHECK_THEN_ACT : EvidenceKind.UNRESOLVED_BOUNDARY, lock,
						"Tests membership before inserting the element.", contains.getStartPosition(), contains.getLength()), //$NON-NLS-1$
				new Evidence(locked ? EvidenceKind.LOCKED_WRITE : EvidenceKind.UNRESOLVED_BOUNDARY, lock,
						"Adds the same element when absent.", add.getStartPosition(), add.getLength())); //$NON-NLS-1$
		var protocol= new ConcurrencyProtocol(identity, summary, ReentrancyContract.UNKNOWN, AnalysisCompleteness.LOCAL_SEED, evidence);
		return Optional.of(new DiagnosticOnly(protocol, Severity.INFO, List.of(reason)));
	}

	private static boolean isListOperation(MethodInvocation invocation, String name) {
		IMethodBinding method= invocation.resolveMethodBinding();
		return method != null && !method.isRecovered() && isType(method.getMethodDeclaration().getDeclaringClass(), LIST)
				&& name.equals(method.getName()) && invocation.arguments().size() == 1 && invocation.getExpression() != null;
	}

	private static Optional<DiagnosticOnly> observe(CompilationUnit unit, EnhancedForStatement loop) {
		Expression iterable= loop.getExpression();
		VariableDeclarationFragment declaration= synchronizedListField(unit, iterable);
		if ((loop.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0 || declaration == null) {
			return Optional.empty();
		}
		IVariableBinding field= declaration.resolveBinding().getVariableDeclaration();
		String fieldKey= field.getKey();
		boolean locked= hasMatchingMonitor(loop, field);
		Evidence evidence= locked
				? new Evidence(EvidenceKind.LOCKED_ITERATION, fieldKey,
						"This loop holds the monitor of the synchronized list field.", //$NON-NLS-1$
						iterable.getStartPosition(), iterable.getLength())
				: new Evidence(EvidenceKind.UNRESOLVED_BOUNDARY, null,
						"The synchronized list monitor could not be verified for this loop.", //$NON-NLS-1$
						iterable.getStartPosition(), iterable.getLength());
		ContainerIdentity identity= new ContainerIdentity(fieldKey, field.getName(),
				declaration.getName().getStartPosition(), declaration.getName().getLength());
		ConcurrencyProfile summary= new ConcurrencyProfile(ThreadExposure.UNKNOWN,
				SynchronizationKind.SYNCHRONIZED_WRAPPER,
				locked ? IterationSemantics.EXTERNALLY_LOCKED : IterationSemantics.UNKNOWN,
				AtomicityRequirement.UNKNOWN, WorkloadShape.UNKNOWN);
		ConcurrencyProtocol protocol= new ConcurrencyProtocol(identity, summary, ReentrancyContract.UNKNOWN,
				AnalysisCompleteness.LOCAL_SEED, List.of(evidence));
		return Optional.of(new DiagnosticOnly(protocol, Severity.INFO, List.of(
				"Traversal of a synchronized list requires holding the returned list's monitor. " //$NON-NLS-1$
						+ "This observation covers the loop's enclosing synchronized blocks. Publication and other accesses remain unverified."))); //$NON-NLS-1$
	}

	static VariableDeclarationFragment synchronizedListField(CompilationUnit unit, Expression expression) {
		return SynchronizedListFieldFacts.synchronizedListField(unit, expression);
	}

	private static Optional<DiagnosticOnly> observeReturn(CompilationUnit unit, ReturnStatement statement) {
		if (statement.getExpression() == null || (statement.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
			return Optional.empty();
		}
		Expression expression= unwrap(statement.getExpression());
		Expression source= expression;
		boolean snapshot= false;
		if (expression instanceof MethodInvocation call) {
			if (!isListCopyOf(call)) {
				return Optional.empty();
			}
			snapshot= true;
			source= (Expression) call.arguments().getFirst();
		}
		VariableDeclarationFragment declaration= synchronizedListField(unit, source);
		if (declaration == null || !isMethodBoundary(statement)) {
			return Optional.empty();
		}
		IVariableBinding field= declaration.resolveBinding().getVariableDeclaration();
		String lock= hasMatchingMonitor(statement, field) ? field.getKey() : null;
		Evidence evidence= new Evidence(snapshot ? EvidenceKind.SNAPSHOT_PUBLICATION : EvidenceKind.LIVE_WRAPPER_RETURN,
				lock, snapshot ? "List.copyOf returns an unmodifiable shallow snapshot of the synchronized list." //$NON-NLS-1$
						: "The caller receives the live synchronized list and can use its monitor.", //$NON-NLS-1$
				expression.getStartPosition(), expression.getLength());
		ContainerIdentity identity= new ContainerIdentity(field.getKey(), field.getName(),
				declaration.getName().getStartPosition(), declaration.getName().getLength());
		ConcurrencyProfile summary= new ConcurrencyProfile(ThreadExposure.UNKNOWN, SynchronizationKind.SYNCHRONIZED_WRAPPER,
				snapshot ? IterationSemantics.IMMUTABLE_SNAPSHOT : IterationSemantics.UNKNOWN,
				AtomicityRequirement.UNKNOWN, WorkloadShape.UNKNOWN);
		ConcurrencyProtocol protocol= new ConcurrencyProtocol(identity, summary, ReentrancyContract.UNKNOWN,
				AnalysisCompleteness.LOCAL_SEED, List.of(evidence));
		return Optional.of(new DiagnosticOnly(protocol, Severity.INFO, List.of(snapshot
				? "Retain snapshot isolation, element identity and null rejection when assessing this return boundary." //$NON-NLS-1$
				: "Callers may mutate or synchronize on the same wrapper. Inspect callers before changing its representation."))); //$NON-NLS-1$
	}

	private static boolean isListCopyOf(MethodInvocation call) {
		IMethodBinding method= call.resolveMethodBinding();
		return method != null && !method.isRecovered() && call.arguments().size() == 1
				&& "copyOf".equals(method.getName()) && Modifier.isStatic(method.getModifiers()) //$NON-NLS-1$
				&& isType(method.getDeclaringClass(), LIST);
	}

	private static boolean isMethodBoundary(ReturnStatement statement) {
		for (ASTNode node= statement.getParent(); node != null; node= node.getParent()) {
			if (node instanceof LambdaExpression || node instanceof AnonymousClassDeclaration || node instanceof AbstractTypeDeclaration) {
				return false;
			}
			if (node instanceof MethodDeclaration method) {
				IMethodBinding binding= method.resolveBinding();
				return binding != null && !binding.isRecovered() && !Modifier.isPrivate(binding.getModifiers());
			}
		}
		return false;
	}

	

	static boolean isType(ITypeBinding type, String qualifiedName) {
		return SynchronizedListFieldFacts.isType(type, qualifiedName);
	}

	private static boolean hasMatchingMonitor(ASTNode site, IVariableBinding field) {
		for (ASTNode parent= site.getParent(); parent != null; parent= parent.getParent()) {
			if (parent instanceof LambdaExpression || parent instanceof MethodDeclaration || parent instanceof Initializer
					|| parent instanceof AbstractTypeDeclaration || parent instanceof AnonymousClassDeclaration) {
				break;
			}
			if (parent instanceof SynchronizedStatement block) {
				IVariableBinding monitor= currentInstanceField(block.getExpression());
				if (monitor != null && field.isEqualTo(monitor)) {
					return true;
				}
			}
		}
		return false;
	}

	static IVariableBinding currentInstanceField(Expression expression) {
		return SynchronizedListFieldFacts.currentInstanceField(expression);
	}

	
}
