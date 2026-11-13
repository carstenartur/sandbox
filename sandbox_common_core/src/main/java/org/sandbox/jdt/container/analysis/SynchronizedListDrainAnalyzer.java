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

import static org.sandbox.jdt.container.analysis.SynchronizedListIterationAnalyzer.currentInstanceField;
import static org.sandbox.jdt.container.analysis.SynchronizedListIterationAnalyzer.isType;
import static org.sandbox.jdt.container.analysis.SynchronizedListIterationAnalyzer.synchronizedListField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RetainExistingLocking;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;

/** Proves the common monitor shared by individual appends and copy-and-clear batch drains. */
final class SynchronizedListDrainAnalyzer {
	private SynchronizedListDrainAnalyzer() {
	}

	private record Drain(VariableDeclarationFragment field, ClassInstanceCreation copy, SimpleName fieldArgument,
			VariableDeclarationFragment batch, MethodInvocation clear, Block body) {}

	static List<ConcurrencyProtocolAssessment> analyze(CompilationUnit unit) {
		Map<VariableDeclarationFragment, Drain> drains= new LinkedHashMap<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation copy) {
				if (copy.arguments().size() != 1 || !(copy.arguments().getFirst() instanceof SimpleName argument)
						|| !isType(copy.resolveTypeBinding(), "java.util.ArrayList") //$NON-NLS-1$
						|| !(copy.getParent() instanceof VariableDeclarationFragment batch)
						|| !(batch.getParent() instanceof VariableDeclarationStatement declaration)
						|| declaration.fragments().size() != 1 || !(declaration.getParent() instanceof Block body)
						|| body.statements().size() < 2 || body.statements().getFirst() != declaration
						|| !(body.statements().get(1) instanceof ExpressionStatement next)
						|| !(next.getExpression() instanceof MethodInvocation clear)
						|| !"clear".equals(clear.getName().getIdentifier())) { //$NON-NLS-1$
					return true;
				}
				var field= synchronizedListField(unit, argument);
				if (field != null && field.resolveBinding().isEqualTo(currentInstanceField(clear.getExpression()))) {
					drains.putIfAbsent(field, new Drain(field, copy, argument, batch, clear, body));
				}
				return true;
			}
		});
		return drains.values().stream().map(drain -> assess(unit, drain)).toList();
	}

	private static ConcurrencyProtocolAssessment assess(CompilationUnit unit, Drain drain) {
		var field= drain.field().resolveBinding();
		String key= field.getKey();
		var identity= new ContainerIdentity(key, field.getName(), drain.field().getName().getStartPosition(),
				drain.field().getName().getLength());
		Set<SimpleName> accesses= new HashSet<>();
		Set<SimpleName> covered= new HashSet<>();
		List<Evidence> evidence= new ArrayList<>();
		ASTNode owner= drain.field().getParent().getParent();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (name.isDeclaration() || !(name.resolveBinding() instanceof IVariableBinding reference)
						|| !field.isEqualTo(reference.getVariableDeclaration())) {
					return true;
				}
				accesses.add(name);
				if (name.getParent() instanceof MethodInvocation add && add.getExpression() == name
						&& isDirectAppend(add, owner)) {
					covered.add(name);
					evidence.add(at(EvidenceKind.LOCKED_WRITE, key, "List.add takes the synchronized wrapper's monitor.", add)); //$NON-NLS-1$
				}
				return true;
			}
		});
		boolean producer= !evidence.isEmpty();
		var monitor= drainMonitor(drain, owner, field);
		if (monitor != null) {
			covered.add(monitor);
			covered.add(drain.fieldArgument());
			covered.add((SimpleName) drain.clear().getExpression());
			evidence.add(at(EvidenceKind.DRAIN, key, "The same monitor spans the ordered snapshot and clear.", drain.copy())); //$NON-NLS-1$
		}
		boolean closed= producer && monitor != null && covered.equals(accesses) && isClosedField(field, owner)
				&& drain.copy().getAnonymousClassDeclaration() == null && Arrays.stream(unit.getProblems()).noneMatch(IProblem::isError);
		if (!closed) {
			String reason= "Verify all appends, escapes and the monitor covering both the copy and clear."; //$NON-NLS-1$
			var unknown= new ConcurrencyProfile(ThreadExposure.UNKNOWN, SynchronizationKind.SYNCHRONIZED_WRAPPER,
					IterationSemantics.UNKNOWN, AtomicityRequirement.UNKNOWN, WorkloadShape.UNKNOWN);
			var protocol= new ConcurrencyProtocol(identity, unknown, ReentrancyContract.UNKNOWN, AnalysisCompleteness.LOCAL_SEED,
					List.of(at(EvidenceKind.UNRESOLVED_BOUNDARY, null, reason, drain.copy())));
			return new DiagnosticOnly(protocol, Severity.INFO, List.of(reason));
		}
		evidence.sort(Comparator.comparingInt(Evidence::sourceStart));
		var summary= new ConcurrencyProfile(ThreadExposure.UNKNOWN, SynchronizationKind.SYNCHRONIZED_WRAPPER,
				IterationSemantics.EXTERNALLY_LOCKED, AtomicityRequirement.DRAIN, WorkloadShape.PRODUCER_CONSUMER);
		var protocol= new ConcurrencyProtocol(identity, summary, ReentrancyContract.NO_CALLBACKS_OBSERVED,
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE, evidence);
		return new RetainExistingLocking(protocol, List.of(
				"The shared monitor prevents appends between the snapshot and clear. Replacing these with individually atomic calls can lose entries.")); //$NON-NLS-1$
	}

	private static SimpleName drainMonitor(Drain drain, ASTNode owner, IVariableBinding field) {
		if (drain.body().getParent() instanceof SynchronizedStatement lock
				&& lock.getExpression() instanceof SimpleName monitor && field.isEqualTo(currentInstanceField(monitor))
				&& lock.getParent() instanceof Block methodBody && methodBody.statements().size() == 1
				&& methodBody.getParent() instanceof MethodDeclaration method && method.getParent() == owner
				&& drain.body().statements().size() == 3 && drain.body().statements().getLast() instanceof ReturnStatement result
				&& result.getExpression() instanceof SimpleName returned && drain.batch().resolveBinding() != null
				&& drain.batch().resolveBinding().isEqualTo(returned.resolveBinding())
				&& drain.clear().getExpression() instanceof SimpleName && drain.clear().arguments().isEmpty()) {
			return monitor;
		}
		return null;
	}

	private static boolean isClosedField(IVariableBinding field, ASTNode owner) {
		var type= field.getType();
		return owner instanceof TypeDeclaration declaration && declaration.isPackageMemberTypeDeclaration()
				&& Modifier.isFinal(declaration.getModifiers()) && declaration.superInterfaceTypes().isEmpty()
				&& declaration.getSuperclassType() == null && field.getAnnotations().length == 0
				&& field.getDeclaringClass().getAnnotations().length == 0
				&& Arrays.stream(declaration.getMethods()).noneMatch(method -> Modifier.isNative(method.getModifiers()))
				&& isType(type, "java.util.List") && type.getTypeArguments().length == 1 //$NON-NLS-1$
				&& isType(type.getTypeArguments()[0], "java.lang.String"); //$NON-NLS-1$
	}

	private static boolean isDirectAppend(MethodInvocation add, ASTNode owner) {
		var binding= add.resolveMethodBinding();
		return binding != null && !binding.isRecovered() && "add".equals(binding.getName()) //$NON-NLS-1$
				&& isType(binding.getDeclaringClass(), "java.util.List") && add.arguments().size() == 1 //$NON-NLS-1$
				&& add.arguments().getFirst() instanceof SimpleName value && add.getParent() instanceof ExpressionStatement statement
				&& statement.getParent() instanceof Block body && body.statements().size() == 1
				&& body.getParent() instanceof MethodDeclaration method && method.getParent() == owner
				&& method.parameters().size() == 1 && method.parameters().getFirst() instanceof SingleVariableDeclaration parameter
				&& parameter.resolveBinding() != null && parameter.resolveBinding().isEqualTo(value.resolveBinding());
	}

	private static Evidence at(EvidenceKind kind, String lock, String summary, ASTNode source) {
		return new Evidence(kind, lock, summary, source.getStartPosition(), source.getLength());
	}
}
