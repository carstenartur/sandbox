/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;

/** Per-use changes. All other source contracts in the component stay on Vintage. */
public record MixedResourcePlan(Map<String, Registration> registrations, Set<String> protectedUnits,
		Map<String, String> sourceFingerprints, List<JUnitBestEffortSupport.Gap> retained) {
	static final MixedResourcePlan EMPTY= new MixedResourcePlan(Map.of(), Set.of(), Map.of(), List.of());
	static final String REGISTER= "org.junit.jupiter.api.extension.RegisterExtension"; //$NON-NLS-1$
	static final String TEST_INSTANCE= "org.junit.jupiter.api.TestInstance"; //$NON-NLS-1$
	static final String PER_METHOD= TEST_INSTANCE + ".Lifecycle.PER_METHOD"; //$NON-NLS-1$
	static final String ISOLATED= "org.junit.jupiter.api.parallel.Isolated"; //$NON-NLS-1$

	public MixedResourcePlan {
		registrations= Map.copyOf(registrations);
		protectedUnits= Set.copyOf(protectedUnits);
		sourceFingerprints= Map.copyOf(sourceFingerprints);
		retained= List.copyOf(retained);
	}

	public record Registration(String fieldKey, String receiverExpression, String registrationName, String parameter, boolean classRule, List<String> orderedTests) {
		public Registration {
			orderedTests= List.copyOf(orderedTests);
		}
		static final String METHOD_ORDER= "@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)"; //$NON-NLS-1$

		String declaration() {
			return "@" + REGISTER + "\n" + (classRule ? "static " : "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ "final org.junit.jupiter.api.extension.Extension " + registrationName + " =\n        " //$NON-NLS-1$ //$NON-NLS-2$
					+ MigrationSupportProvider.FACTORY + (classRule ? ".perClass" : ".perTest") //$NON-NLS-1$ //$NON-NLS-2$
					+ "(() -> " + receiverExpression + ", " + parameter + " -> " + parameter + ".before(), " + parameter + " -> " + parameter + ".after());"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	public boolean retains(ICompilationUnit unit) {
		String handle= unit.getPrimary().getHandleIdentifier();
		return protectedUnits.contains(handle) && !registrations.containsKey(handle);
	}

	void addOperations(ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> processed) throws CoreException {
		var registration= registrations.get(unit.getPrimary().getHandleIdentifier());
		if (registration == null) {
			if (retains(unit)) root.accept(new ASTVisitor() {
				@Override public void preVisit(ASTNode node) {
					if (node instanceof BodyDeclaration || node instanceof Annotation) processed.add(node);
				}
			});
			return;
		}
		var project= unit.getJavaProject();
		if (!MigrationSupportProvider.available(project)) throw stale();
		var current= JavaProjectCompilationUnits.collect(project);
		if (sourceFingerprints.size() != current.size()) throw stale();
		for (var source : current) {
			if (!JUnit4ParameterizedPlan.fingerprint(source.getPrimary().getSource())
					.equals(sourceFingerprints.get(source.getPrimary().getHandleIdentifier()))) throw stale();
		}
		if (root.types().size() != 1 || !(root.types().getFirst() instanceof TypeDeclaration owner)) throw stale();
		for (FieldDeclaration field : owner.getFields()) {
			if (field.fragments().size() != 1) continue;
			var fragment= (VariableDeclarationFragment) field.fragments().getFirst();
			var binding= fragment.resolveBinding();
			if (binding == null || binding.isRecovered() || !registration.fieldKey().equals(binding.getKey())) continue;
			Annotation rule= JUnitMigrationPlan.findRuleAnnotation(field, registration.classRule());
			if (rule == null || !MixedResourcePlanner.currentOwnerProof(unit, registration, sourceFingerprints.keySet(),
					new ExternalResourceReferenceSearch(List.of(project), current))) throw stale();
			processed.add(field);
			processed.add(rule);
			operations.add(new CompilationUnitRewriteOperationWithSourceRange() {
				@Override public void rewriteASTInternal(CompilationUnitRewrite cu, LinkedProposalModelCore linked) {
					var rewrite= cu.getASTRewrite();
					var group= createTextEditGroup("Register the existing fixture at its original rule scope", cu); //$NON-NLS-1$
					rewrite.remove(rule, group);
					cu.getImportRemover().registerRemovedNode(rule);
					var annotation= owner.getAST().newMarkerAnnotation();
					annotation.setTypeName(owner.getAST().newName(ISOLATED));
					var modifiers= rewrite.getListRewrite(owner, TypeDeclaration.MODIFIERS2_PROPERTY);
					modifiers.insertFirst(annotation, group);
					var lifecycle= owner.getAST().newSingleMemberAnnotation();
					lifecycle.setTypeName(owner.getAST().newName(TEST_INSTANCE));
					lifecycle.setValue(owner.getAST().newName(PER_METHOD));
					modifiers.insertFirst(lifecycle, group);
					if (!registration.orderedTests().isEmpty()) {
						modifiers.insertFirst(rewrite.createStringPlaceholder(Registration.METHOD_ORDER, ASTNode.SINGLE_MEMBER_ANNOTATION), group);
						for (var method : owner.getMethods()) {
							int order= registration.orderedTests().indexOf(method.getName().getIdentifier());
							if (order >= 0 && !method.isConstructor() && method.parameters().isEmpty()) {
								var rank= owner.getAST().newSingleMemberAnnotation();
								rank.setTypeName(owner.getAST().newName("org.junit.jupiter.api.Order")); //$NON-NLS-1$
								rank.setValue(owner.getAST().newNumberLiteral(Integer.toString(order + 1)));
								rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY).insertFirst(rank, group);
							}
						}
					}
					var declaration= rewrite.createStringPlaceholder(registration.declaration(), ASTNode.FIELD_DECLARATION);
					rewrite.getListRewrite(owner, TypeDeclaration.BODY_DECLARATIONS_PROPERTY).insertAfter(declaration, field, group);
				}
			});
			return;
		}
		throw stale();
	}

	private static CoreException stale() {
		return new CoreException(Status.error("The mixed ExternalResource sources, bindings or support provider changed. Retry the complete cleanup.")); //$NON-NLS-1$
	}
}
