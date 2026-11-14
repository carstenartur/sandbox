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
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;
import static org.sandbox.jdt.container.analysis.SynchronizedListFieldFacts.isType;

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RecommendedMigration;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RetainExistingLocking;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Strategy;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.TargetContainerContract;

/** Binding-indexed field observations and closed, diagnostic-only FIFO and pending-work protocols. */
public final class ContainerConcurrencyAnalyzer {
	private ContainerConcurrencyAnalyzer() {}

	private static final String LIST= "java.util.List"; //$NON-NLS-1$
	private static final String SET= "java.util.Set"; //$NON-NLS-1$
	private static final String STRING= "java.lang.String"; //$NON-NLS-1$

	private record Field(VariableDeclarationFragment declaration, IVariableBinding binding, SynchronizationKind kind,
			List<SimpleName> references) {}
	private record Lock(String key, String label, SynchronizationKind kind) {}

	/** Returns one assessment per field; every evidence range belongs to the supplied compilation unit. */
	public static List<ConcurrencyProtocolAssessment> analyze(CompilationUnit root, Runnable checkCanceled) {
		return analyze(root, checkCanceled, List.of());
	}

	/** Omits observations already presented by the specialized drain, iteration and publication analyzers. */
	public static List<ConcurrencyProtocolAssessment> analyze(CompilationUnit root, Runnable checkCanceled,
			Collection<? extends ConcurrencyProtocolAssessment> prior) {
		Map<String, Field> fields= new LinkedHashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(FieldDeclaration member) {
				checkCanceled.run();
				for (Object raw : member.fragments()) {
					var variable= (VariableDeclarationFragment) raw;
					var binding= variable.resolveBinding();
					if (binding != null && !binding.isRecovered() && binding.getKey() != null) {
						var kind= candidate(variable, binding);
						if (kind != null) {
							fields.put(binding.getKey(), new Field(variable, binding, kind, new ArrayList<>()));
						}
					}
				}
				return true;
			}
		});
		if (fields.isEmpty()) {
			return List.of();
		}
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				checkCanceled.run();
				return true;
			}
			@Override
			public boolean visit(SimpleName name) {
				if (!name.isDeclaration() && name.resolveBinding() instanceof IVariableBinding binding && binding.isField()) {
					Field field= fields.get(binding.getVariableDeclaration().getKey());
					if (field != null) {
						field.references().add(name);
					}
				}
				return true;
			}
		});
		var output= new ArrayList<ConcurrencyProtocolAssessment>();
		var membership= fields.values().stream().anyMatch(f -> f.kind() == SynchronizationKind.SYNCHRONIZED_WRAPPER && isType(f.binding().getType(), SET))
				? ConcurrentMembershipAnalyzer.analyze(root) : Map.<String, ConcurrentMembershipAnalyzer.Field>of();
		var listeners= fields.values().stream().anyMatch(f -> f.kind() == SynchronizationKind.NONE && isType(f.binding().getType(), LIST)
				&& f.binding().getType().getTypeArguments().length == 1 && isType(f.binding().getType().getTypeArguments()[0], "java.lang.Runnable")) //$NON-NLS-1$
				? ListenerSnapshotAnalyzer.analyze(root) : Map.<String, ListenerSnapshotAnalyzer.Observed>of();
		for (Field field : fields.values()) {
			checkCanceled.run();
			var detected= assess(root, field, membership.get(field.binding().getKey()), listeners.get(field.binding().getKey()));
			if (detected instanceof DiagnosticOnly diagnostic) {
				var existing= prior.stream().filter(report -> report.protocol().identity().stableId().equals(field.binding().getKey())).toList();
				if (existing.stream().anyMatch(RetainExistingLocking.class::isInstance)) {
					continue;
				}
				var protocol= diagnostic.protocol();
				var unique= protocol.evidence().stream().filter(candidate -> existing.stream().flatMap(report -> report.protocol().evidence().stream())
						.noneMatch(done -> done.sourceStart() <= candidate.sourceStart()
								&& done.sourceStart() + done.sourceLength() >= candidate.sourceStart() + candidate.sourceLength())).toList();
				if (!unique.isEmpty()) {
					output.add(new DiagnosticOnly(new ConcurrencyProtocol(protocol.identity(), protocol.summary(), protocol.reentrancy(),
							protocol.completeness(), unique), diagnostic.severity(), diagnostic.explanations()));
				}
			} else if (detected != null) {
				output.add(detected);
			}
		}
		return List.copyOf(output);
	}

	private static SynchronizationKind candidate(VariableDeclarationFragment declaration, IVariableBinding binding) {
		var type= binding.getType();
		if (Modifier.isVolatile(binding.getModifiers()) && type.isArray() && !type.getElementType().isPrimitive()) {
			return SynchronizationKind.VOLATILE_SNAPSHOT;
		}
		if (isType(type, "java.util.concurrent.atomic.AtomicReference") && type.getTypeArguments().length == 1 //$NON-NLS-1$
				&& (type.getTypeArguments()[0].isArray() || collection(type.getTypeArguments()[0]))) { //$NON-NLS-1$
			return SynchronizationKind.ATOMIC_REFERENCE;
		}
		if (type.getErasure().getQualifiedName().startsWith("java.util.concurrent.") && !type.isFromSource() //$NON-NLS-1$
				&& !type.getErasure().getQualifiedName().startsWith("java.util.concurrent.locks.")) { //$NON-NLS-1$
			return collection(type) ? SynchronizationKind.CONCURRENT_COLLECTION : null;
		}
		if (declaration.getInitializer() instanceof MethodInvocation wrapper && wrapper.resolveMethodBinding() != null
				&& isType(wrapper.resolveMethodBinding().getDeclaringClass(), "java.util.Collections") //$NON-NLS-1$
				&& Set.of("synchronizedList", "synchronizedSet", "synchronizedMap").contains(wrapper.getName().getIdentifier())) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return SynchronizationKind.SYNCHRONIZED_WRAPPER;
		}
		return collection(type) ? SynchronizationKind.NONE : null;
	}

	private static boolean collection(ITypeBinding type) {
		if (type == null || type.isRecovered()) {
			return false;
		}
		if (isType(type, "java.util.Collection") || isType(type, "java.util.Map")) { //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		}
		return Arrays.stream(type.getInterfaces()).anyMatch(ContainerConcurrencyAnalyzer::collection) || collection(type.getSuperclass());
	}

	private static ConcurrencyProtocolAssessment assess(CompilationUnit root, Field field, ConcurrentMembershipAnalyzer.Field membership,
			ListenerSnapshotAnalyzer.Observed listener) {
		var evidence= new ArrayList<Evidence>();
		boolean hasLock= false;
		boolean explicit= false;
		boolean fifo= false;
		boolean batch= isType(field.binding().getType(), SET);
		boolean hasClear= false;
		boolean traversed= false;
		boolean onlyLockedTraversal= true;
		boolean offered= false;
		for (var name : field.references()) {
			Expression ref= reference(name);
			Lock lock= enclosingLock(ref);
			hasLock |= lock != null;
			explicit |= lock != null && lock.kind() == SynchronizationKind.EXPLICIT_LOCK;
			if (ref.getParent() instanceof SynchronizedStatement used && used.getExpression() == ref) {
				continue;
			}
			boolean ownerLock= lock != null && lock.key().equals(field.binding().getKey());
			if (ref.getParent() instanceof EnhancedForStatement loop && loop.getExpression() == ref) {
				traversed= true;
				onlyLockedTraversal &= ownerLock && simpleTraversal(loop);
			} else if (ref.getParent() instanceof MethodInvocation write && write.getExpression() == ref && plainAppend(write, field)) {
				offered= true;
			} else {
				onlyLockedTraversal= false;
			}
			if (ref.getParent() instanceof MethodInvocation op && op.getExpression() == ref) {
				String method= op.getName().getIdentifier();
				fifo |= frontRemove(op);
				hasClear |= "clear".equals(method); //$NON-NLS-1$
				var kind= classifyCall(op);
				if (kind == EvidenceKind.UNRESOLVED_BOUNDARY) {
					evidence.add(at(kind, lock == null ? "" : lock.key(), "Iterator, stream, callback or unclassified operation needs a separate lifetime and locking proof.", op)); //$NON-NLS-1$ //$NON-NLS-2$
				} else if (lock == null && field.kind() == SynchronizationKind.SYNCHRONIZED_WRAPPER) {
					evidence.add(at(kind, field.binding().getKey(), "The wrapper synchronizes the individual call to " + method + ".", op)); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					addAccess(evidence, lock, kind, "Call to " + method, op); //$NON-NLS-1$
				}
			} else if (ref.getParent() instanceof EnhancedForStatement loop && loop.getExpression() == ref) {
				addAccess(evidence, lock, EvidenceKind.LOCKED_ITERATION, "Encounter-order traversal", ref); //$NON-NLS-1$
				observeCallbacks(loop, evidence);
			} else if (ref.getParent() instanceof ReturnStatement) {
				evidence.add(at(EvidenceKind.LIVE_WRAPPER_RETURN, "", "The field value escapes to its caller; inspect aliases and external mutation.", ref)); //$NON-NLS-1$ //$NON-NLS-2$
			} else if (ref.getParent() instanceof Assignment write && write.getLeftHandSide() == ref
					&& field.kind() == SynchronizationKind.VOLATILE_SNAPSHOT) {
				evidence.add(at(EvidenceKind.SNAPSHOT_PUBLICATION, "", "Volatile replacement publishes the array reference. Verify ownership and later element writes.", write)); //$NON-NLS-1$ //$NON-NLS-2$
			} else if (ref.getParent() instanceof ClassInstanceCreation copy && copy.arguments().contains(ref)) {
				addAccess(evidence, lock, EvidenceKind.LOCKED_READ, "Collection passed to the copy constructor", copy); //$NON-NLS-1$
			} else {
				evidence.add(at(EvidenceKind.UNRESOLVED_BOUNDARY, "", "Inspect this alias, callback or published value before changing its protocol.", ref)); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		if (evidence.isEmpty() || field.kind() == SynchronizationKind.NONE && !hasLock) {
			return null;
		}
		var kind= field.kind() == SynchronizationKind.NONE ? (explicit ? SynchronizationKind.EXPLICIT_LOCK : SynchronizationKind.INTRINSIC_LOCK) : field.kind();
		var identity= new ContainerIdentity(field.binding().getKey(), field.binding().getName(), field.declaration().getName().getStartPosition(),
				field.declaration().getName().getLength());
		if (field.kind() == SynchronizationKind.SYNCHRONIZED_WRAPPER && traversed && onlyLockedTraversal
				&& Modifier.isPrivate(field.binding().getModifiers()) && Modifier.isFinal(field.binding().getModifiers())
				&& field.declaration().getParent().getParent() instanceof TypeDeclaration owner && Modifier.isFinal(owner.getModifiers())
				&& closedOwner(root, field, owner) && field.declaration().getInitializer() instanceof MethodInvocation wrapper
				&& wrapper.arguments().size() == 1 && wrapper.arguments().getFirst() instanceof ClassInstanceCreation fresh
				&& fresh.getAnonymousClassDeclaration() == null && fresh.arguments().isEmpty() && isType(fresh.resolveTypeBinding(), "java.util.ArrayList")) { //$NON-NLS-1$
			var profile= new ConcurrencyProfile(ThreadExposure.UNKNOWN, kind, IterationSemantics.EXTERNALLY_LOCKED, AtomicityRequirement.TRANSACTION, WorkloadShape.UNKNOWN);
			return new RetainExistingLocking(new ConcurrencyProtocol(identity, profile, ReentrancyContract.NO_CALLBACKS_OBSERVED, AnalysisCompleteness.LOCAL_USAGE_COMPLETE, evidence),
					List.of(offered ? "All traversals hold the wrapper's own monitor. Individual appends acquire that monitor; no alias escapes and traversal invokes no callbacks." //$NON-NLS-1$
							: "All traversals hold the wrapper's own monitor; the private backing collection has no escaping reference, mutation or callback.")); //$NON-NLS-1$
		}
		var callbacks= evidence.stream().anyMatch(e -> e.kind() == EvidenceKind.CALLBACK_UNDER_LOCK) ? ReentrancyContract.CALLBACKS_UNDER_LOCK
				: evidence.stream().anyMatch(e -> e.kind() == EvidenceKind.CALLBACK_OUTSIDE_LOCK) ? ReentrancyContract.CALLBACKS_OUTSIDE_LOCK : ReentrancyContract.UNKNOWN;
		var strategy= fifo ? Strategy.CONCURRENT_QUEUE : batch && hasClear ? Strategy.PURPOSE_SPECIFIC_ABSTRACTION : null;
		if (strategy != null && closedProtocol(root, field, strategy)) {
			boolean queue= strategy == Strategy.CONCURRENT_QUEUE;
			String reason= queue ? "Non-null tail insertions and a single locked empty-test/head removal implement FIFO without waiting, aliases, callbacks or size-based synchronization. A poll-based queue preserves order and duplicates." //$NON-NLS-1$
					: "An atomic drain must copy and clear under the same monitor. A project-local pending-work abstraction can preserve first registration order and uniqueness, including null; separate concurrent clear and iteration can lose offers."; //$NON-NLS-1$
			var profile= new ConcurrencyProfile(ThreadExposure.UNKNOWN, kind, IterationSemantics.EXTERNALLY_LOCKED,
					queue ? AtomicityRequirement.CHECK_THEN_ACT : AtomicityRequirement.DRAIN, WorkloadShape.PRODUCER_CONSUMER);
			var protocol= new ConcurrencyProtocol(identity, profile, ReentrancyContract.NO_CALLBACKS_OBSERVED, AnalysisCompleteness.LOCAL_USAGE_COMPLETE, evidence);
			var target= new TargetContainerContract(queue ? ContainerShape.DEQUE : ContainerShape.CUSTOM_BUFFER, OrderRequirement.ENCOUNTER,
					queue ? UniquenessRequirement.DUPLICATES_ALLOWED : UniquenessRequirement.REQUIRED, Mutability.MUTABLE,
					queue ? NullContract.REJECTED : NullContract.ALLOWED, reason);
			return new RecommendedMigration(protocol, target, strategy, Confidence.HIGH, List.of(reason));
		}
		if ((membership != null && membership.rejection().isEmpty()) || (listener != null && listener.plan().rejection().isEmpty())) {
			boolean registrations= listener != null;
			if (registrations) {
				for (var notification : listener.notifications()) {
					evidence.add(at(EvidenceKind.CALLBACK_OUTSIDE_LOCK, "", "Notification traverses the detached array outside the registration monitor.", notification.loop())); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			String proof= registrations
					? "Registrations append under one monitor, including duplicates and null. Each notification copies the complete ordered registry before invoking callbacks outside the lock. Reentrant registration affects the next dispatch. Copy-on-write moves allocation to each registration; assess write frequency." //$NON-NLS-1$
					: "Every reference is one non-null String membership operation. No external lock, compound check, iteration, callback, order observation or snapshot escapes. ConcurrentHashMap.newKeySet preserves the operation result and duplicate policy."; //$NON-NLS-1$
			var profile= new ConcurrencyProfile(registrations ? ThreadExposure.CALLBACK_SHARED : ThreadExposure.UNKNOWN, kind,
					registrations ? IterationSemantics.IMMUTABLE_SNAPSHOT : IterationSemantics.UNKNOWN, AtomicityRequirement.INDIVIDUAL_OPERATIONS, WorkloadShape.UNKNOWN);
			var protocol= new ConcurrencyProtocol(identity, profile, registrations ? ReentrancyContract.CALLBACKS_OUTSIDE_LOCK : ReentrancyContract.NO_CALLBACKS_OBSERVED,
					AnalysisCompleteness.LOCAL_USAGE_COMPLETE, evidence);
			var target= new TargetContainerContract(registrations ? ContainerShape.LIST : ContainerShape.SET,
					registrations ? OrderRequirement.ENCOUNTER : OrderRequirement.NONE, registrations ? UniquenessRequirement.DUPLICATES_ALLOWED : UniquenessRequirement.REQUIRED,
					Mutability.MUTABLE, registrations ? NullContract.ALLOWED : NullContract.REJECTED, proof);
			return new RecommendedMigration(protocol, target, registrations ? Strategy.COPY_ON_WRITE_SEQUENCE : Strategy.CONCURRENT_MEMBERSHIP_SET, Confidence.HIGH, List.of(proof));
		}

		String reason= "Inspect lock identities across all accesses, snapshot ownership, callback reentrancy and external aliases. Preserve observable order, duplicate handling and null behavior. Frequency and external thread boundaries are unknown."; //$NON-NLS-1$
		if (strategy != null) {
			reason= "Verify the common monitor across check/remove or copy/clear, nonblocking behavior, producer arguments and all escapes before choosing a concurrent target."; //$NON-NLS-1$
			evidence.add(at(EvidenceKind.UNRESOLVED_BOUNDARY, "", reason, field.declaration().getName())); //$NON-NLS-1$
		}
		evidence.sort(Comparator.comparingInt(Evidence::sourceStart));
		var profile= new ConcurrencyProfile(ThreadExposure.UNKNOWN, kind, IterationSemantics.UNKNOWN,
				strategy == null ? AtomicityRequirement.UNKNOWN : fifo ? AtomicityRequirement.CHECK_THEN_ACT : AtomicityRequirement.DRAIN,
				WorkloadShape.UNKNOWN);
		return new DiagnosticOnly(new ConcurrencyProtocol(identity, profile, callbacks, AnalysisCompleteness.LOCAL_SEED, evidence),
				Severity.INFO, List.of(reason));
	}

	private static boolean plainAppend(MethodInvocation write, Field field) {
		return "add".equals(write.getName().getIdentifier()) && write.arguments().size() == 1 //$NON-NLS-1$
				&& write.resolveMethodBinding() != null && isType(write.resolveMethodBinding().getDeclaringClass(), LIST)
				&& write.getParent() instanceof ExpressionStatement statement && statement.getParent() instanceof Block body
				&& body.statements().size() == 1 && body.getParent() instanceof MethodDeclaration method
				&& method.getParent() == field.declaration().getParent().getParent()
				&& validProducer(method, (Expression) write.arguments().getFirst(), Strategy.PURPOSE_SPECIFIC_ABSTRACTION);
	}

	private static boolean simpleTraversal(EnhancedForStatement loop) {
		// Calls can execute arbitrary code even when their body contains no collection reference.
		boolean[] simple= {true};
		loop.getBody().accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				if (!(node instanceof Block || node instanceof EmptyStatement || node instanceof ExpressionStatement
						|| node instanceof PostfixExpression || node instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding var
								&& !var.isField() && var.getType().isPrimitive())) {
					simple[0]= false;
				}
				return simple[0];
			}
		});
		return simple[0];
	}

	private static EvidenceKind classifyCall(MethodInvocation call) {
		var method= call.resolveMethodBinding();
		if (method == null || method.isRecovered() || method.getMethodDeclaration().getDeclaringClass().isFromSource()) {
			return EvidenceKind.UNRESOLVED_BOUNDARY;
		}
		return switch (method.getName()) {
			case "add", "addAll", "remove", "removeAll", "retainAll", "clear", "set", "put", "offer", "getAndSet", "compareAndSet" -> EvidenceKind.LOCKED_WRITE; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
			case "contains", "containsAll", "get", "indexOf", "lastIndexOf", "isEmpty", "size", "toArray" -> EvidenceKind.LOCKED_READ; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
			default -> EvidenceKind.UNRESOLVED_BOUNDARY;
		};
	}

	private static boolean frontRemove(MethodInvocation op) {
		return "remove".equals(op.getName().getIdentifier()) && op.arguments().size() == 1 //$NON-NLS-1$
				&& op.arguments().getFirst() instanceof NumberLiteral zero && "0".equals(zero.getToken()) //$NON-NLS-1$
				&& op.resolveMethodBinding() != null && isType(op.resolveMethodBinding().getDeclaringClass(), LIST)
				&& op.resolveMethodBinding().getParameterTypes()[0].isPrimitive();
	}

	private static Expression reference(SimpleName name) {
		if (name.getParent() instanceof FieldAccess access && access.getName() == name) {
			return access;
		}
		if (name.getParent() instanceof QualifiedName qualified && qualified.getName() == name) {
			return qualified;
		}
		return name;
	}

	private static void addAccess(List<Evidence> evidence, Lock lock, EvidenceKind lockedKind, String action, ASTNode site) {
		evidence.add(lock == null
				? at(EvidenceKind.UNRESOLVED_BOUNDARY, "", action + " without a proven enclosing lock. Verify publication and access semantics.", site) //$NON-NLS-1$ //$NON-NLS-2$
				: at(lockedKind, lock.key(), action + " under " + lock.label() + ".", site)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Evidence at(EvidenceKind kind, String key, String message, ASTNode node) {
		return new Evidence(kind, key, message, node.getStartPosition(), node.getLength());
	}

	private static void observeCallbacks(EnhancedForStatement loop, List<Evidence> evidence) {
		var element= loop.getParameter().resolveBinding();
		loop.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation call) {
				if (element != null && variableBinding(call.getExpression()).filter(element::isEqualTo).isPresent()) {
					var lock= enclosingLock(call);
					evidence.add(at(lock == null ? EvidenceKind.CALLBACK_OUTSIDE_LOCK : EvidenceKind.CALLBACK_UNDER_LOCK,
							lock == null ? "" : lock.key(), "Element callback under " + (lock == null ? "no proven monitor" : lock.label()) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
									+ ". Inspect whether registration can change reentrantly.", call)); //$NON-NLS-1$
				}
				return true;
			}
		});
	}

	private static Lock enclosingLock(ASTNode site) {
		for (ASTNode parent= site.getParent(); parent != null; parent= parent.getParent()) {
			if (parent instanceof SynchronizedStatement monitor && !inside(site, monitor.getExpression())) {
				return lock(monitor.getExpression(), SynchronizationKind.INTRINSIC_LOCK);
			}
			if (parent instanceof TryStatement attempt && inside(site, attempt.getBody())) {
				Lock guard= explicitLock(attempt);
				if (guard != null) {
					return guard;
				}
			}
			if (parent instanceof MethodDeclaration method) {
				if (Modifier.isSynchronized(method.getModifiers()) && method.resolveBinding() != null) {
					var owner= method.resolveBinding().getDeclaringClass();
					boolean statik= Modifier.isStatic(method.getModifiers());
					return new Lock(owner.getKey() + (statik ? ".class" : ".this"), statik ? owner.getName() + ".class" : "this", SynchronizationKind.INTRINSIC_LOCK); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				}
				break;
			}
			if (parent instanceof LambdaExpression || parent instanceof AbstractTypeDeclaration || parent instanceof AnonymousClassDeclaration) {
				break;
			}
		}
		return null;
	}

	private static boolean inside(ASTNode node, ASTNode ancestor) {
		for (ASTNode current= node; current != null; current= current.getParent()) {
			if (current == ancestor) {
				return true;
			}
		}
		return false;
	}

	private static Lock lock(Expression expr, SynchronizationKind kind) {
		if (unwrap(expr) instanceof ThisExpression self && self.resolveTypeBinding() != null) {
			return new Lock(self.resolveTypeBinding().getKey() + ".this", self.toString(), kind); //$NON-NLS-1$
		}
		var binding= variableBinding(expr).orElse(null);
		// Distinct receivers of obj.field and reassigned lock variables are not the same monitor.
		boolean current= unwrap(expr) instanceof SimpleName || unwrap(expr) instanceof FieldAccess access && access.getExpression() instanceof ThisExpression;
		return binding != null && current && (Modifier.isFinal(binding.getModifiers()) || !binding.isField() && binding.isEffectivelyFinal())
				? new Lock(binding.getVariableDeclaration().getKey(), expr.toString(), kind) : null;
	}

	private static Lock explicitLock(TryStatement attempt) {
		if (!(attempt.getParent() instanceof Block body) || attempt.getFinally() == null || attempt.getFinally().statements().size() != 1) {
			return null;
		}
		int i= body.statements().indexOf(attempt);
		if (i < 1) {
			return null;
		}
		Expression acquired= lockReceiver(body.statements().get(i - 1), "lock"); //$NON-NLS-1$
		Expression released= lockReceiver(attempt.getFinally().statements().getFirst(), "unlock"); //$NON-NLS-1$
		if (acquired == null || released == null || !attempt.catchClauses().isEmpty() || !attempt.resources().isEmpty()) {
			return null;
		}
		Lock guard= lock(acquired, SynchronizationKind.EXPLICIT_LOCK);
		Lock release= lock(released, SynchronizationKind.EXPLICIT_LOCK);
		if (guard == null || release == null || !guard.key().equals(release.key())) {
			return null;
		}
		var binding= variableBinding(acquired).orElseThrow();
		// Only the directly paired lock/try/finally proves the lexical critical section.
		// An early unlock or an alias in the try body invalidates that proof.
		boolean[] usedInBody= { false };
		attempt.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				usedInBody[0] |= binding.isEqualTo(name.resolveBinding());
				return !usedInBody[0];
			}
		});
		return usedInBody[0] ? null : guard;
	}

	private static Expression lockReceiver(Object statement, String method) {
		if (statement instanceof ExpressionStatement s && s.getExpression() instanceof MethodInvocation call && call.arguments().isEmpty()
				&& method.equals(call.getName().getIdentifier()) && call.resolveMethodBinding() != null
				&& (isType(call.resolveMethodBinding().getDeclaringClass(), "java.util.concurrent.locks.Lock") //$NON-NLS-1$
						|| isType(call.resolveMethodBinding().getDeclaringClass(), "java.util.concurrent.locks.ReentrantLock"))) { //$NON-NLS-1$
			return call.getExpression();
		}
		return null;
	}

	private static boolean closedProtocol(CompilationUnit root, Field field, Strategy strategy) {
		var binding= field.binding();
		if (field.kind() != SynchronizationKind.SYNCHRONIZED_WRAPPER || !Modifier.isPrivate(binding.getModifiers())
				|| !Modifier.isFinal(binding.getModifiers()) || Modifier.isStatic(binding.getModifiers()) || binding.getAnnotations().length != 0
				|| binding.getType().getTypeArguments().length != 1 || !isType(binding.getType().getTypeArguments()[0], STRING)
				|| !(field.declaration().getParent().getParent() instanceof TypeDeclaration owner) || !owner.isPackageMemberTypeDeclaration()
				|| !Modifier.isFinal(owner.getModifiers()) || owner.getSuperclassType() != null || !owner.superInterfaceTypes().isEmpty()
				|| !closedOwner(root, field, owner)
				|| !(field.declaration().getInitializer() instanceof MethodInvocation wrapper) || wrapper.arguments().size() != 1
				|| !(wrapper.arguments().getFirst() instanceof ClassInstanceCreation backing) || !backing.arguments().isEmpty()
				|| backing.getAnonymousClassDeclaration() != null
				|| !isType(backing.resolveTypeBinding(), strategy == Strategy.CONCURRENT_QUEUE ? "java.util.ArrayList" : "java.util.LinkedHashSet")) { //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		var covered= new HashSet<SimpleName>();
		boolean produced= false;
		boolean consumed= false;
		for (MethodDeclaration method : owner.getMethods()) {
			if (method.getBody() == null || method.getBody().statements().size() != 1 || Modifier.isSynchronized(method.getModifiers())) {
				continue;
			}
			Object stmt= method.getBody().statements().getFirst();
			if (stmt instanceof ExpressionStatement write && write.getExpression() instanceof MethodInvocation add
					&& "add".equals(add.getName().getIdentifier()) && add.arguments().size() == 1 && add.getExpression() instanceof SimpleName name //$NON-NLS-1$
					&& binding.isEqualTo(name.resolveBinding()) && validProducer(method, (Expression) add.arguments().getFirst(), strategy)) {
				covered.add(name);
				produced= true;
			}
			if (stmt instanceof SynchronizedStatement lock && lock.getExpression() instanceof SimpleName monitor && binding.isEqualTo(monitor.resolveBinding())
					&& consumer(lock.getBody(), binding, strategy, covered)) {
				covered.add(monitor);
				consumed= true;
			}
		}
		return produced && consumed && covered.equals(Set.copyOf(field.references()));
	}

	private static boolean closedOwner(CompilationUnit root, Field field, TypeDeclaration owner) {
		return owner.isPackageMemberTypeDeclaration() && owner.getSuperclassType() == null && owner.superInterfaceTypes().isEmpty()
				&& field.binding().getAnnotations().length == 0 && field.binding().getDeclaringClass().getAnnotations().length == 0
				&& Arrays.stream(owner.getMethods()).noneMatch(method -> Modifier.isNative(method.getModifiers()))
				&& Arrays.stream(root.getProblems()).noneMatch(IProblem::isError);
	}

	private static boolean validProducer(MethodDeclaration method, Expression argument, Strategy strategy) {
		if (method.parameters().size() != 1 || !(method.parameters().getFirst() instanceof SingleVariableDeclaration parameter) || parameter.resolveBinding() == null) {
			return false;
		}
		if (strategy == Strategy.CONCURRENT_QUEUE) {
			if (!(argument instanceof MethodInvocation guard) || guard.arguments().size() != 1 || guard.resolveMethodBinding() == null
					|| !isType(guard.resolveMethodBinding().getDeclaringClass(), "java.util.Objects") || !"requireNonNull".equals(guard.getName().getIdentifier())) { //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			}
			argument= (Expression) guard.arguments().getFirst();
		}
		return argument instanceof SimpleName value && parameter.resolveBinding().isEqualTo(value.resolveBinding());
	}

	private static boolean consumer(Block body, IVariableBinding field, Strategy strategy, Set<SimpleName> covered) {
		if (strategy == Strategy.CONCURRENT_QUEUE) {
			if (body.statements().size() != 1 || !(body.statements().getFirst() instanceof ReturnStatement result)
					|| !(result.getExpression() instanceof ConditionalExpression take) || !(take.getExpression() instanceof MethodInvocation empty)
					|| !empty.arguments().isEmpty() || !"isEmpty".equals(empty.getName().getIdentifier()) || !(take.getThenExpression() instanceof NullLiteral) //$NON-NLS-1$
					|| !(take.getElseExpression() instanceof MethodInvocation remove) || !frontRemove(remove)
					|| !(empty.getExpression() instanceof SimpleName check) || !field.isEqualTo(check.resolveBinding())
					|| !(remove.getExpression() instanceof SimpleName pop) || !field.isEqualTo(pop.resolveBinding())) {
				return false;
			}
			covered.addAll(List.of(check, pop));
			return true;
		}
		if (body.statements().size() != 3 || !(body.statements().getFirst() instanceof VariableDeclarationStatement save) || save.fragments().size() != 1
				|| !(save.fragments().getFirst() instanceof VariableDeclarationFragment snapshot) || !(snapshot.getInitializer() instanceof ClassInstanceCreation copy)
				|| !isType(copy.resolveTypeBinding(), "java.util.LinkedHashSet") || copy.getAnonymousClassDeclaration() != null || copy.arguments().size() != 1 //$NON-NLS-1$
				|| !(copy.arguments().getFirst() instanceof SimpleName source) || !field.isEqualTo(source.resolveBinding())
				|| !(body.statements().get(1) instanceof ExpressionStatement second) || !(second.getExpression() instanceof MethodInvocation clear)
				|| !"clear".equals(clear.getName().getIdentifier()) || !clear.arguments().isEmpty() || !(clear.getExpression() instanceof SimpleName destination) //$NON-NLS-1$
				|| !field.isEqualTo(destination.resolveBinding()) || !(body.statements().getLast() instanceof ReturnStatement returned)
				|| !(returned.getExpression() instanceof SimpleName result) || !snapshot.resolveBinding().isEqualTo(result.resolveBinding())) {
			return false;
		}
		covered.addAll(List.of(source, destination));
		return true;
	}
}
