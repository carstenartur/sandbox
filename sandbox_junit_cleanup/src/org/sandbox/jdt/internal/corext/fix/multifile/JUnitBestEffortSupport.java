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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_CLASS_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXPECTED_EXCEPTION;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_TEMPORARY_FOLDER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNWITH;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.ParameterizedMigrationEligibility;
import org.sandbox.jdt.internal.corext.fix.helper.RuleExpectedExceptionJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTemporayFolderJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RunWithMigrationEligibility;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TestNameRefactorer;

/**
 * Explicit support for a deliberately non-atomic JUnit migration mode.
 *
 * <p>The normal cleanup remains fail closed. When best-effort mode is selected,
 * independently safe rewrites may proceed while every known unsupported
 * construct is represented by deterministic diagnostics and a compilable
 * {@code @todo} method scaffold in the affected source type. The scaffold is a
 * marker, not a guessed implementation.</p>
 */
public final class JUnitBestEffortSupport {

	private static final String TEST_NAME= "org.junit.rules.TestName"; //$NON-NLS-1$
	private static final String TIMEOUT= "org.junit.rules.Timeout"; //$NON-NLS-1$
	private static final String ERROR_COLLECTOR= "org.junit.rules.ErrorCollector"; //$NON-NLS-1$
	private static final String MARKER_PREFIX= "Sandbox JUnit migration gap "; //$NON-NLS-1$

	/** One unresolved construct and the exact source type where it must be completed. */
	public record Gap(String ownerCompilationUnitHandle, String targetTypeBindingKey,
			String targetTypeName, int targetTypeStart, String candidateId, String reasonCode,
			String explanation, String remediation) {

		/** Validates and normalizes a best-effort gap. */
		public Gap {
			ownerCompilationUnitHandle= Objects.requireNonNull(ownerCompilationUnitHandle);
			targetTypeBindingKey= targetTypeBindingKey == null ? "" : targetTypeBindingKey; //$NON-NLS-1$
			targetTypeName= Objects.requireNonNull(targetTypeName);
			candidateId= Objects.requireNonNull(candidateId);
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
			remediation= Objects.requireNonNull(remediation);
		}

		String markerText() {
			return MARKER_PREFIX + candidateId + " (" + reasonCode + "): " + explanation //$NON-NLS-1$ //$NON-NLS-2$
					+ " Manual completion: " + remediation; //$NON-NLS-1$
		}
	}

	/** Deterministic analysis retained between project planning and per-file fixes. */
	public record Analysis(List<Gap> gaps, boolean disableCoordinatedExternalResource) {

		/** Copies and orders all gaps. */
		public Analysis {
			gaps= gaps == null ? List.of() : gaps.stream()
					.sorted(Comparator.comparing(Gap::ownerCompilationUnitHandle)
							.thenComparing(Gap::targetTypeName)
							.thenComparing(Gap::candidateId)
							.thenComparing(Gap::reasonCode))
					.toList();
		}

		/** Returns an empty analysis for strict mode. */
		public static Analysis empty() {
			return new Analysis(List.of(), false);
		}

		/** Returns gaps belonging to one primary compilation unit. */
		public List<Gap> gapsFor(ICompilationUnit unit) {
			if (unit == null) {
				return List.of();
			}
			String handle= unit.getPrimary().getHandleIdentifier();
			return gaps.stream().filter(gap -> handle.equals(gap.ownerCompilationUnitHandle())).toList();
		}

		/** Wraps ordinary planner evidence with explicit manual-completion evidence. */
		public String toJson(String plannerDiagnosticsJson) {
			StringBuilder json= new StringBuilder(512);
			json.append('{')
					.append("\"bestEffort\":true,") //$NON-NLS-1$
					.append("\"manualCompletionRequired\":").append(!gaps.isEmpty()).append(',') //$NON-NLS-1$
					.append("\"coordinatedExternalResourceDeferred\":") //$NON-NLS-1$
					.append(disableCoordinatedExternalResource).append(',')
					.append("\"planner\":"); //$NON-NLS-1$
			if (plannerDiagnosticsJson == null || plannerDiagnosticsJson.isBlank()) {
				json.append("null"); //$NON-NLS-1$
			} else {
				json.append(plannerDiagnosticsJson);
			}
			json.append(",\"gaps\":["); //$NON-NLS-1$
			for (int index= 0; index < gaps.size(); index++) {
				if (index > 0) {
					json.append(',');
				}
				Gap gap= gaps.get(index);
				json.append('{')
						.append("\"unitId\":\"").append(jsonEscape(opaqueId(gap.ownerCompilationUnitHandle()))) //$NON-NLS-1$
						.append("\",\"type\":\"").append(jsonEscape(gap.targetTypeName())) //$NON-NLS-1$
						.append("\",\"candidateId\":\"").append(jsonEscape(gap.candidateId())) //$NON-NLS-1$
						.append("\",\"reasonCode\":\"").append(jsonEscape(gap.reasonCode())) //$NON-NLS-1$
						.append("\",\"explanation\":\"").append(jsonEscape(gap.explanation())) //$NON-NLS-1$
						.append("\",\"remediation\":\"").append(jsonEscape(gap.remediation())) //$NON-NLS-1$
						.append("\"}"); //$NON-NLS-1$
			}
			return json.append("]}").toString(); //$NON-NLS-1$
		}
	}

	private record SourceType(String unitHandle, String bindingKey, String name, int start,
			TypeDeclaration declaration, ITypeBinding binding) {
	}

	private static final class ResourceUsage {
		private final ITypeBinding binding;
		private final List<SourceType> owners= new ArrayList<>();
		private boolean instanceRule;
		private boolean classRule;

		ResourceUsage(ITypeBinding binding) {
			this.binding= binding;
		}
	}

	private JUnitBestEffortSupport() {
	}

	/**
	 * Returns rewrites that do not change test discovery, lifecycle, runners or
	 * extension contracts and may therefore proceed beside a quarantined gap.
	 */
	public static EnumSet<JUnitCleanUpFixCore> independentlySafeFixes(
			EnumSet<JUnitCleanUpFixCore> fixes) {
		EnumSet<JUnitCleanUpFixCore> result= fixes.clone();
		result.retainAll(EnumSet.of(
				JUnitCleanUpFixCore.ASSERT,
				JUnitCleanUpFixCore.ASSERT_OPTIMIZATION,
				JUnitCleanUpFixCore.ASSUME,
				JUnitCleanUpFixCore.ASSUME_OPTIMIZATION));
		return result;
	}

	/** Analyzes all enabled difficult JUnit 4 constructs before cleanup planning. */
	public static Analysis analyze(IJavaProject project, ICompilationUnit[] units,
			EnumSet<JUnitCleanUpFixCore> fixes, IProgressMonitor monitor) {
		Objects.requireNonNull(project);
		Objects.requireNonNull(units);
		Objects.requireNonNull(fixes);
		if (units.length == 0 || fixes.isEmpty()) {
			return Analysis.empty();
		}
		Map<String, CompilationUnit> roots= parse(project, units, monitor);
		Map<String, SourceType> sourceTypesByKey= new LinkedHashMap<>();
		Map<String, Gap> gaps= new LinkedHashMap<>();
		Map<String, ResourceUsage> usages= new LinkedHashMap<>();

		for (Map.Entry<String, CompilationUnit> entry : roots.entrySet()) {
			String handle= entry.getKey();
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration node) {
					ITypeBinding binding= node.resolveBinding();
					String key= JUnitMigrationPlan.typeKey(binding);
					SourceType type= new SourceType(handle, key == null ? "" : key, //$NON-NLS-1$
							sourceTypeName(node), node.getStartPosition(), node, binding);
					if (key != null) {
						sourceTypesByKey.put(key, type);
					}
					boolean parameterizedRunner=
							ParameterizedMigrationEligibility.hasParameterizedRunner(node);
					if (parameterizedRunner && fixes.contains(JUnitCleanUpFixCore.PARAMETERIZED)) {
						ParameterizedMigrationEligibility.Assessment assessment=
								ParameterizedMigrationEligibility.assess(node);
						if (!assessment.eligible()) {
							addGap(gaps, type, "parameterized:" + type.name(), //$NON-NLS-1$
									assessment.reasonCode(), assessment.explanation(),
									remediationFor(assessment.reasonCode()));
						}
					} else if (parameterizedRunner && requiresParameterizedClosure(fixes)) {
						String reasonCode= "PARAMETERIZED_COMPONENT_NOT_SELECTED"; //$NON-NLS-1$
						addGap(gaps, type, "parameterized:" + type.name(), //$NON-NLS-1$
								reasonCode,
								"A selected rewrite would change JUnit discovery, lifecycle or Rule semantics while the JUnit 4 Parameterized runner remains.", //$NON-NLS-1$
								remediationFor(reasonCode));
					}
					if (fixes.contains(JUnitCleanUpFixCore.RUNWITH)) {
						String runner= runnerType(node);
						if (runner != null && !runner.isBlank()
								&& !parameterizedRunner
								&& !RunWithMigrationEligibility.canMigrate(node, runner)) {
							addGap(gaps, type, "runner:" + type.name(), //$NON-NLS-1$
									"CUSTOM_JUNIT4_RUNNER", //$NON-NLS-1$
									"The JUnit 4 runner " + runner //$NON-NLS-1$
											+ " has no generally equivalent automatic Jupiter rewrite.", //$NON-NLS-1$
									"Port the runner behavior to one or more Jupiter extensions, or retain the Vintage engine for this test type."); //$NON-NLS-1$
						}
					}
					return true;
				}
			});
		}

		for (Map.Entry<String, CompilationUnit> entry : roots.entrySet()) {
			String handle= entry.getKey();
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(FieldDeclaration field) {
					TypeDeclaration ownerDeclaration= enclosingType(field);
					if (ownerDeclaration == null) {
						return true;
					}
					SourceType owner= sourceType(handle, ownerDeclaration);
					ITypeBinding fieldType= field.getType().resolveBinding();
					String qualifiedType= qualifiedName(fieldType);
					if (fixes.contains(JUnitCleanUpFixCore.RULETEMPORARYFOLDER)
							&& ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(qualifiedType)) {
						RuleTemporayFolderJUnitPlugin.Assessment assessment=
								RuleTemporayFolderJUnitPlugin.assess(field);
						if (!assessment.eligible()) {
							addGap(gaps, owner, fieldCandidate("temporary-folder", field), //$NON-NLS-1$
									assessment.reasonCode(), assessment.explanation(),
									remediationFor(assessment.reasonCode()));
						}
					}
					if (fixes.contains(JUnitCleanUpFixCore.RULETESTNAME)
							&& TEST_NAME.equals(qualifiedType)) {
						TestNameRefactorer.Assessment assessment= TestNameRefactorer.assess(field);
						if (!assessment.eligible()) {
							addGap(gaps, owner, fieldCandidate("test-name", field), //$NON-NLS-1$
									assessment.reasonCode(), assessment.explanation(),
									remediationFor(assessment.reasonCode()));
						}
					}
					if (fixes.contains(JUnitCleanUpFixCore.RULEEXPECTEDEXCEPTION)
							&& ORG_JUNIT_RULES_EXPECTED_EXCEPTION.equals(qualifiedType)) {
						RuleExpectedExceptionJUnitPlugin.Assessment assessment=
								RuleExpectedExceptionJUnitPlugin.assess(field);
						if (!assessment.eligible()) {
							addGap(gaps, owner, fieldCandidate("expected-exception", field), //$NON-NLS-1$
									assessment.reasonCode(), assessment.explanation(),
									remediationFor(assessment.reasonCode()));
						}
					}

					RuleKind ruleKind= ruleKind(field);
					if (ruleKind != RuleKind.NONE) {
						ITypeBinding resource= resourceTypeBinding(field);
						if (extendsExternalResource(resource)) {
							String resourceKey= JUnitMigrationPlan.typeKey(resource);
							String usageKey= resourceKey == null ? qualifiedName(resource) : resourceKey;
							if (usageKey != null && !usageKey.isBlank()) {
								ResourceUsage usage= usages.computeIfAbsent(usageKey,
										ignored -> new ResourceUsage(resource));
								usage.owners.add(owner);
								usage.instanceRule|= ruleKind == RuleKind.INSTANCE;
								usage.classRule|= ruleKind == RuleKind.CLASS;
							}
						} else if (rulesEnabled(fixes) && !isKnownRuleType(qualifiedType)) {
							addGap(gaps, owner, fieldCandidate("rule", field), //$NON-NLS-1$
									"UNSUPPORTED_JUNIT4_RULE", //$NON-NLS-1$
									"The rule type " + (qualifiedType == null ? "<unresolved>" : qualifiedType) //$NON-NLS-1$ //$NON-NLS-2$
											+ " has no proven automatic Jupiter equivalent.", //$NON-NLS-1$
									"Implement the rule contract as a Jupiter extension, register it explicitly, and then remove the JUnit 4 rule field."); //$NON-NLS-1$
						}
					}
					return true;
				}
			});
		}

		boolean mixedLifecycle= false;
		for (Map.Entry<String, ResourceUsage> entry : usages.entrySet()) {
			ResourceUsage usage= entry.getValue();
			if (!usage.instanceRule || !usage.classRule) {
				continue;
			}
			mixedLifecycle= true;
			SourceType target= sourceTypesByKey.get(entry.getKey());
			if (target == null && !usage.owners.isEmpty()) {
				target= usage.owners.get(0);
			}
			if (target != null) {
				String resourceName= qualifiedName(usage.binding);
				addGap(gaps, target, "external-resource:" //$NON-NLS-1$
						+ (resourceName == null ? target.name() : resourceName),
						"MIXED_RULE_LIFECYCLE", //$NON-NLS-1$
						"The same ExternalResource fixture is used by both @Rule and @ClassRule, so one Jupiter callback lifecycle cannot preserve both contracts.", //$NON-NLS-1$
						"Split the fixture into separate instance and class extensions, migrate each callback lifecycle, and update the corresponding fields."); //$NON-NLS-1$
			}
		}
		return new Analysis(new ArrayList<>(gaps.values()), mixedLifecycle);
	}

	/** Adds one idempotent marker operation for the supplied compilation-unit gaps. */
	public static void addMarkerOperation(CompilationUnit root, List<Gap> gaps,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations) {
		if (root == null || gaps == null || gaps.isEmpty()) {
			return;
		}
		Map<TypeDeclaration, List<Gap>> resolved= new LinkedHashMap<>();
		List<TypeDeclaration> types= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				types.add(node);
				return true;
			}
		});
		for (Gap gap : gaps) {
			TypeDeclaration target= types.stream().filter(type -> matches(type, gap)).findFirst().orElse(null);
			if (target == null && !types.isEmpty()) {
				target= types.get(0);
			}
			if (target != null) {
				resolved.computeIfAbsent(target, ignored -> new ArrayList<>()).add(gap);
			}
		}
		if (!resolved.isEmpty()) {
			operations.add(new GapMarkerOperation(resolved));
		}
	}

	private enum RuleKind {
		NONE,
		INSTANCE,
		CLASS
	}

	private static final class GapMarkerOperation extends CompilationUnitRewriteOperationWithSourceRange {
		private final Map<TypeDeclaration, List<Gap>> gapsByType;

		GapMarkerOperation(Map<TypeDeclaration, List<Gap>> gapsByType) {
			this.gapsByType= gapsByType;
		}

		@Override
		public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			TextEditGroup group= createTextEditGroup(
					"Mark incomplete best-effort JUnit migrations", cuRewrite); //$NON-NLS-1$
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= cuRewrite.getRoot().getAST();
			for (Map.Entry<TypeDeclaration, List<Gap>> entry : gapsByType.entrySet()) {
				TypeDeclaration type= entry.getKey();
				Set<String> methodNames= new LinkedHashSet<>();
				for (MethodDeclaration method : type.getMethods()) {
					methodNames.add(method.getName().getIdentifier());
				}
				ListRewrite body= rewrite.getListRewrite(type, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
				for (Gap gap : entry.getValue()) {
					if (hasMarker(type, gap)) {
						continue;
					}
					MethodDeclaration marker= markerMethod(ast, gap, methodNames);
					methodNames.add(marker.getName().getIdentifier());
					body.insertLast(marker, group);
				}
			}
		}
	}

	private static MethodDeclaration markerMethod(AST ast, Gap gap, Set<String> existingNames) {
		MethodDeclaration method= ast.newMethodDeclaration();
		String baseName= markerMethodName(gap.reasonCode());
		String name= baseName;
		for (int suffix= 2; existingNames.contains(name); suffix++) {
			name= baseName + suffix;
		}
		method.setName(ast.newSimpleName(name));
		method.setReturnType2(ast.newPrimitiveType(org.eclipse.jdt.core.dom.PrimitiveType.VOID));
		method.modifiers().add(ast.newModifier(org.eclipse.jdt.core.dom.Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		method.modifiers().add(ast.newModifier(org.eclipse.jdt.core.dom.Modifier.ModifierKeyword.STATIC_KEYWORD));

		Javadoc javadoc= ast.newJavadoc();
		TagElement todo= ast.newTagElement();
		todo.setTagName("@todo"); //$NON-NLS-1$
		TextElement text= ast.newTextElement();
		text.setText(javadocText(gap.markerText()));
		todo.fragments().add(text);
		javadoc.tags().add(todo);
		method.setJavadoc(javadoc);

		ClassInstanceCreation exception= ast.newClassInstanceCreation();
		exception.setType(ast.newSimpleType(ast.newSimpleName("UnsupportedOperationException"))); //$NON-NLS-1$
		StringLiteral message= ast.newStringLiteral();
		message.setLiteralValue("Manual JUnit migration required: " + gap.reasonCode()); //$NON-NLS-1$
		exception.arguments().add(message);
		ThrowStatement throwStatement= ast.newThrowStatement();
		throwStatement.setExpression(exception);
		org.eclipse.jdt.core.dom.Block body= ast.newBlock();
		body.statements().add(throwStatement);
		method.setBody(body);
		return method;
	}

	private static boolean hasMarker(TypeDeclaration type, Gap gap) {
		String marker= MARKER_PREFIX + gap.candidateId();
		for (MethodDeclaration method : type.getMethods()) {
			Javadoc javadoc= method.getJavadoc();
			if (javadoc != null && javadoc.toString().contains(marker)) {
				return true;
			}
		}
		return false;
	}

	private static String markerMethodName(String reasonCode) {
		StringBuilder name= new StringBuilder("sandboxJUnitMigrationTodo"); //$NON-NLS-1$
		for (String part : reasonCode.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) { //$NON-NLS-1$
			if (!part.isEmpty()) {
				name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		if (name.length() > 100) {
			name.setLength(88);
			name.append(Integer.toUnsignedString(reasonCode.hashCode(), 16));
		}
		return name.toString();
	}

	private static boolean matches(TypeDeclaration type, Gap gap) {
		String key= JUnitMigrationPlan.typeKey(type.resolveBinding());
		if (!gap.targetTypeBindingKey().isBlank() && gap.targetTypeBindingKey().equals(key)) {
			return true;
		}
		return gap.targetTypeStart() == type.getStartPosition()
				&& gap.targetTypeName().equals(sourceTypeName(type));
	}

	private static Map<String, CompilationUnit> parse(IJavaProject project, ICompilationUnit[] units,
			IProgressMonitor monitor) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units, new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(source.getPrimary().getHandleIdentifier(), ast);
			}
		}, monitor);
		return roots;
	}

	private static SourceType sourceType(String handle, TypeDeclaration type) {
		ITypeBinding binding= type.resolveBinding();
		String key= JUnitMigrationPlan.typeKey(binding);
		return new SourceType(handle, key == null ? "" : key, sourceTypeName(type), //$NON-NLS-1$
				type.getStartPosition(), type, binding);
	}

	private static TypeDeclaration enclosingType(ASTNode node) {
		for (ASTNode current= node.getParent(); current != null; current= current.getParent()) {
			if (current instanceof TypeDeclaration type) {
				return type;
			}
		}
		return null;
	}

	private static String fieldCandidate(String kind, FieldDeclaration field) {
		if (field.fragments().size() == 1
				&& field.fragments().get(0) instanceof VariableDeclarationFragment fragment) {
			return kind + ':' + fragment.getName().getIdentifier();
		}
		return kind + ":field@" + field.getStartPosition(); //$NON-NLS-1$
	}

	private static void addGap(Map<String, Gap> gaps, SourceType type, String candidateId,
			String reasonCode, String explanation, String remediation) {
		Gap gap= new Gap(type.unitHandle(), type.bindingKey(), type.name(), type.start(), candidateId,
				reasonCode, explanation, remediation);
		gaps.putIfAbsent(type.unitHandle() + '|' + candidateId + '|' + reasonCode, gap);
	}

	private static RuleKind ruleKind(FieldDeclaration field) {
		for (Object modifier : field.modifiers()) {
			if (!(modifier instanceof Annotation annotation)) {
				continue;
			}
			ITypeBinding binding= annotation.resolveTypeBinding();
			String name= qualifiedName(binding);
			if (ORG_JUNIT_RULE.equals(name)) {
				return RuleKind.INSTANCE;
			}
			if (ORG_JUNIT_CLASS_RULE.equals(name)) {
				return RuleKind.CLASS;
			}
		}
		return RuleKind.NONE;
	}

	private static ITypeBinding resourceTypeBinding(FieldDeclaration field) {
		if (field.fragments().size() == 1
				&& field.fragments().get(0) instanceof VariableDeclarationFragment fragment) {
			if (fragment.getInitializer() instanceof ClassInstanceCreation creation) {
				ITypeBinding created= creation.resolveTypeBinding();
				if (created != null) {
					return created;
				}
			}
			IVariableBinding variable= fragment.resolveBinding();
			if (variable != null) {
				return variable.getType();
			}
		}
		return field.getType().resolveBinding();
	}

	private static boolean extendsExternalResource(ITypeBinding binding) {
		for (ITypeBinding current= binding; current != null; current= current.getSuperclass()) {
			if (ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(qualifiedName(current))) {
				return true;
			}
		}
		return false;
	}

	private static String runnerType(TypeDeclaration type) {
		for (Object modifier : type.modifiers()) {
			if (!(modifier instanceof Annotation annotation)) {
				continue;
			}
			ITypeBinding annotationType= annotation.resolveTypeBinding();
			if (!ORG_JUNIT_RUNWITH.equals(qualifiedName(annotationType))) {
				continue;
			}
			org.eclipse.jdt.core.dom.Expression value= annotationValue(annotation);
			if (value instanceof TypeLiteral literal) {
				return qualifiedName(literal.getType().resolveBinding());
			}
			return "<unresolved>"; //$NON-NLS-1$
		}
		return null;
	}

	private static org.eclipse.jdt.core.dom.Expression annotationValue(Annotation annotation) {
		if (annotation instanceof SingleMemberAnnotation single) {
			return single.getValue();
		}
		if (annotation instanceof NormalAnnotation normal) {
			for (Object value : normal.values()) {
				if (value instanceof MemberValuePair pair
						&& "value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
					return pair.getValue();
				}
			}
		}
		return null;
	}


	private static boolean requiresParameterizedClosure(EnumSet<JUnitCleanUpFixCore> fixes) {
		EnumSet<JUnitCleanUpFixCore> structural= fixes.clone();
		structural.removeAll(independentlySafeFixes(fixes));
		return !structural.isEmpty();
	}

	private static boolean rulesEnabled(EnumSet<JUnitCleanUpFixCore> fixes) {
		return fixes.contains(JUnitCleanUpFixCore.RULETEMPORARYFOLDER)
				|| fixes.contains(JUnitCleanUpFixCore.RULETESTNAME)
				|| fixes.contains(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE)
				|| fixes.contains(JUnitCleanUpFixCore.RULETIMEOUT)
				|| fixes.contains(JUnitCleanUpFixCore.RULEEXPECTEDEXCEPTION)
				|| fixes.contains(JUnitCleanUpFixCore.RULEERRORCOLLECTOR);
	}

	private static boolean isKnownRuleType(String qualifiedType) {
		return ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(qualifiedType)
				|| TEST_NAME.equals(qualifiedType)
				|| ORG_JUNIT_RULES_EXPECTED_EXCEPTION.equals(qualifiedType)
				|| TIMEOUT.equals(qualifiedType)
				|| ERROR_COLLECTOR.equals(qualifiedType)
				|| ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(qualifiedType);
	}

	private static String sourceTypeName(TypeDeclaration type) {
		ITypeBinding binding= type.resolveBinding();
		String qualified= qualifiedName(binding);
		return qualified == null || qualified.isBlank() ? type.getName().getIdentifier() : qualified;
	}

	private static String qualifiedName(ITypeBinding binding) {
		return binding == null ? null : binding.getErasure().getQualifiedName();
	}

	private static String remediationFor(String reasonCode) {
		if ("PARAMETERIZED_COMPONENT_NOT_SELECTED".equals(reasonCode)) { //$NON-NLS-1$
			return "Enable the Parameterized migration for the same source component, or leave its JUnit 4 test, lifecycle and Rule annotations unchanged until that coordinated migration can run."; //$NON-NLS-1$
		}
		if (reasonCode.startsWith("PARAMETERIZED_")) { //$NON-NLS-1$
			return "Replace field injection or the custom provider with explicit Jupiter method arguments/Arguments sources, then remove the Parameterized runner and constructor coupling."; //$NON-NLS-1$
		}
		if (reasonCode.startsWith("TEMPORARY_FOLDER_")) { //$NON-NLS-1$
			return "Introduce @TempDir Path and translate the remaining custom folder semantics to java.nio.file.Files calls before removing TemporaryFolder."; //$NON-NLS-1$
		}
		if (reasonCode.startsWith("TEST_NAME_")) { //$NON-NLS-1$
			return "Inject TestInfo in @BeforeEach and replace every remaining rule use with the intended method-name or display-name contract."; //$NON-NLS-1$
		}
		if (reasonCode.startsWith("EXPECTED_EXCEPTION_")) { //$NON-NLS-1$
			return "Wrap only the throwing statements in assertThrows(), then express message, cause and matcher checks with explicit Jupiter assertions."; //$NON-NLS-1$
		}
		return "Complete this construct manually using the Jupiter extension and assertion APIs described by the reason code."; //$NON-NLS-1$
	}

	private static String javadocText(String value) {
		return value.replace("*/", "* /").replace('\r', ' ').replace('\n', ' '); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String opaqueId(String value) {
		try {
			byte[] digest= MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
					.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String jsonEscape(String value) {
		StringBuilder escaped= new StringBuilder(value.length() + 16);
		for (int index= 0; index < value.length(); index++) {
			char character= value.charAt(index);
			switch (character) {
			case '"' -> escaped.append("\\\""); //$NON-NLS-1$
			case '\\' -> escaped.append("\\\\"); //$NON-NLS-1$
			case '\b' -> escaped.append("\\b"); //$NON-NLS-1$
			case '\f' -> escaped.append("\\f"); //$NON-NLS-1$
			case '\n' -> escaped.append("\\n"); //$NON-NLS-1$
			case '\r' -> escaped.append("\\r"); //$NON-NLS-1$
			case '\t' -> escaped.append("\\t"); //$NON-NLS-1$
			default -> {
				if (character < 0x20) {
					escaped.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(character))); //$NON-NLS-1$
				} else {
					escaped.append(character);
				}
			}
			}
		}
		return escaped.toString();
	}
}
