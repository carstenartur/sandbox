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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
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

/**
 * Source-backed, report-only access inventory for private synchronized-list fields.
 *
 * <p>Each distinct field gets one protocol with deterministically ordered source spans.
 * A synchronized wrapper protects individual method calls, not a sequence of calls,
 * and even an apparently complete compilation unit does not prove caller closure.
 * Accordingly this extractor never promotes its observations to a migration proof.</p>
 */
public final class SynchronizedListAccessProtocolAnalyzer {

	/** Extract observations only when source bindings prove the exact field and JDK factory. */
	public List<DiagnosticOnly> analyze(CompilationUnit unit) {
		if (unit == null) {
			return List.of();
		}
		Map<String, FieldObservations> fields= new LinkedHashMap<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (name.isDeclaration()) {
					return true;
				}
				Expression reference= name;
				if (name.getParent() instanceof FieldAccess access && access.getName() == name) {
					reference= access;
				}
				VariableDeclarationFragment declaration= SynchronizedListFieldFacts.synchronizedListField(unit, reference);
				if (declaration == null || declaration.resolveBinding() == null) {
					return true;
				}
				IVariableBinding field= declaration.resolveBinding().getVariableDeclaration();
				FieldObservations observations= fields.computeIfAbsent(field.getKey(),
						key -> new FieldObservations(new ContainerIdentity(key, field.getName(),
								declaration.getName().getStartPosition(), declaration.getName().getLength())));
				Evidence observation= classify(reference, field);
				if (observation != null) {
					observations.evidence.add(observation);
				}
				return true;
			}
		});
		List<DiagnosticOnly> reports= new ArrayList<>();
		for (FieldObservations observations : fields.values()) {
			if (observations.evidence.isEmpty()) {
				continue;
			}
			observations.evidence.sort(Comparator.comparingInt(Evidence::sourceStart)
					.thenComparingInt(Evidence::sourceLength));
			boolean unresolved= observations.evidence.stream()
					.anyMatch(e -> e.kind() == EvidenceKind.UNRESOLVED_BOUNDARY
							|| e.kind() == EvidenceKind.LIVE_WRAPPER_RETURN);
			ConcurrencyProfile profile= new ConcurrencyProfile(ThreadExposure.UNKNOWN,
					SynchronizationKind.SYNCHRONIZED_WRAPPER, IterationSemantics.UNKNOWN,
					AtomicityRequirement.UNKNOWN, WorkloadShape.UNKNOWN);
			ConcurrencyProtocol protocol= new ConcurrencyProtocol(observations.identity, profile,
					ReentrancyContract.UNKNOWN, AnalysisCompleteness.LOCAL_SEED,
					observations.evidence);
			String explanation= unresolved
					? "Some container accesses or publication boundaries remain unproven; no rewrite is authorized." //$NON-NLS-1$
					: "Only direct source accesses were classified. Compound operations, callers and publication remain unproven."; //$NON-NLS-1$
			reports.add(new DiagnosticOnly(protocol, unresolved ? Severity.WARNING : Severity.INFO,
					List.of(explanation)));
		}
		return List.copyOf(reports);
	}

	private static Evidence classify(Expression reference, IVariableBinding field) {
		ASTNode parent= reference.getParent();
		if (parent instanceof SynchronizedStatement monitor && monitor.getExpression() == reference) {
			return null; // Taking a monitor does not by itself access or expose the list.
		}
		String key= field.getKey();
		if (parent instanceof EnhancedForStatement loop && loop.getExpression() == reference) {
			if (holdsMonitor(loop, field)) {
				return at(EvidenceKind.LOCKED_ITERATION, key,
						"The same wrapper monitor covers this traversal.", reference); //$NON-NLS-1$
			}
			return unknown("Iteration without a proven enclosing wrapper monitor.", reference); //$NON-NLS-1$
		}
		if (parent instanceof ReturnStatement statement && statement.getExpression() == reference) {
			return at(EvidenceKind.LIVE_WRAPPER_RETURN, null,
					"Returns the live wrapper; caller monitor and mutation behavior require source-closure proof.", reference); //$NON-NLS-1$
		}
		if (parent instanceof MethodInvocation invocation) {
			if (invocation.getExpression() == reference) {
				return methodAccess(invocation, field);
			}
			IMethodBinding method= invocation.resolveMethodBinding();
			if (method != null && !method.isRecovered() && "copyOf".equals(method.getName()) //$NON-NLS-1$
					&& SynchronizedListFieldFacts.isType(method.getDeclaringClass(), "java.util.List") //$NON-NLS-1$
					&& invocation.arguments().size() == 1
					&& invocation.arguments().getFirst() == reference) {
				return at(EvidenceKind.SNAPSHOT_PUBLICATION, null,
						"Copies the current list; a concurrent writer may still affect the copy unless locked.", invocation); //$NON-NLS-1$
			}
		}
		return unknown("Field reference requires an escape, alias or compound-operation proof.", reference); //$NON-NLS-1$
	}

	private static Evidence methodAccess(MethodInvocation invocation, IVariableBinding field) {
		IMethodBinding method= invocation.resolveMethodBinding();
		if (method == null || method.isRecovered() || !isListMethod(method)) {
			return unknown("Unresolved or non-JDK list method on the synchronized wrapper.", invocation); //$NON-NLS-1$
		}
		String name= method.getName();
		EvidenceKind kind= switch (name) {
			case "add", "addAll", "clear", "remove", "removeAll", "retainAll", "set", "replaceAll", "sort", "removeIf" -> //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
				EvidenceKind.LOCKED_WRITE;
			case "contains", "containsAll", "get", "indexOf", "lastIndexOf", "isEmpty", "size", "toArray" -> //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
				EvidenceKind.LOCKED_READ;
			default -> EvidenceKind.UNRESOLVED_BOUNDARY;
		};
		if (kind == EvidenceKind.UNRESOLVED_BOUNDARY) {
			return unknown("Iterator, stream or unclassified operation needs a protocol proof.", invocation); //$NON-NLS-1$
		}
		return at(kind, field.getKey(),
				"Individual synchronized-wrapper call; no compound atomicity is inferred.", invocation); //$NON-NLS-1$
	}

	private static boolean isListMethod(IMethodBinding binding) {
		var declaring= binding.getMethodDeclaration().getDeclaringClass();
		return SynchronizedListFieldFacts.isType(declaring, "java.util.List") //$NON-NLS-1$
				|| SynchronizedListFieldFacts.isType(declaring, "java.util.Collection"); //$NON-NLS-1$
	}

	private static boolean holdsMonitor(ASTNode site, IVariableBinding field) {
		for (ASTNode parent= site.getParent(); parent != null; parent= parent.getParent()) {
			if (parent instanceof LambdaExpression || parent instanceof MethodDeclaration
					|| parent instanceof Initializer || parent instanceof AbstractTypeDeclaration
					|| parent instanceof AnonymousClassDeclaration) {
				return false;
			}
			if (parent instanceof SynchronizedStatement block) {
				IVariableBinding monitor= SynchronizedListFieldFacts.currentInstanceField(block.getExpression());
				if (monitor != null && field.isEqualTo(monitor)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Evidence unknown(String explanation, ASTNode node) {
		return at(EvidenceKind.UNRESOLVED_BOUNDARY, null, explanation, node);
	}

	private static Evidence at(EvidenceKind kind, String lock, String explanation, ASTNode node) {
		return new Evidence(kind, lock, explanation, node.getStartPosition(), node.getLength());
	}

	private static final class FieldObservations {
		final ContainerIdentity identity;
		final List<Evidence> evidence= new ArrayList<>();

		FieldObservations(ContainerIdentity identity) {
			this.identity= identity;
		}
	}
}
