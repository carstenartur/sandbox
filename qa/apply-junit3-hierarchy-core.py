#!/usr/bin/env python3
"""Apply the planned JUnit 3 hierarchy compatibility and lifecycle patch once."""

from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact replacement, found {count}")
    file.write_text(text.replace(old, new), encoding="utf-8")


def regex_once(path: str, pattern: str, replacement: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{path}: expected one regex replacement, found {count}")
    file.write_text(updated, encoding="utf-8")


migration = "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnit3HierarchyMigration.java"
replace_once(
    migration,
    "\t\tBEFORE_EACH,\n\t\tAFTER_EACH\n",
    "\t\tBEFORE_EACH,\n\t\tAFTER_EACH,\n\t\tREMOVE_COMPATIBILITY_MEMBER\n",
)

harness = "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnit3HarnessSemantics.java"
replace_once(harness, "import org.eclipse.jdt.core.dom.Type;\n",
             "import org.eclipse.jdt.core.dom.Type;\nimport org.eclipse.jdt.core.dom.TypeDeclaration;\n")
replace_once(
    harness,
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SuiteModel;\n",
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3LegacyShape;\n"
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SuiteModel;\n",
)
replace_once(
    harness,
    "\tstatic Optional<Rejection> rejection(MethodDeclaration method) {\n\t\tObjects.requireNonNull(method);\n",
    "\tstatic Optional<Rejection> rejection(MethodDeclaration method) {\n"
    "\t\tTypeDeclaration owner= method != null && method.getParent() instanceof TypeDeclaration type ? type : null;\n"
    "\t\treturn rejection(method, owner);\n"
    "\t}\n\n"
    "\tstatic Optional<Rejection> rejection(MethodDeclaration method, TypeDeclaration owner) {\n"
    "\t\tObjects.requireNonNull(method);\n",
)
replace_once(
    harness,
    "\t\tif (method.isConstructor()) {\n"
    "\t\t\tif (isNamedTestConstructor(method)) {\n"
    "\t\t\t\treturn Optional.of(new Rejection(\"NAMED_JUNIT3_TEST_CONSTRUCTION\", //$NON-NLS-1$\n"
    "\t\t\t\t\t\t\"The hierarchy constructs named JUnit 3 test instances through a String constructor; preserving selected method identity requires an explicit framework migration.\")); //$NON-NLS-1$\n"
    "\t\t\t}\n"
    "\t\t\treturn Optional.of(new Rejection(\"CUSTOM_JUNIT3_CONSTRUCTOR\", //$NON-NLS-1$\n"
    "\t\t\t\t\t\"The hierarchy declares an explicit constructor whose instantiation contract is not represented by ordinary Jupiter test discovery.\")); //$NON-NLS-1$\n"
    "\t\t}\n",
    "\t\tif (method.isConstructor()) {\n"
    "\t\t\tif (JUnit3LegacyShape.isRemovableConstructor(method)) {\n"
    "\t\t\t\treturn Optional.empty();\n"
    "\t\t\t}\n"
    "\t\t\tif (isNamedTestConstructor(method)) {\n"
    "\t\t\t\treturn Optional.of(new Rejection(\"NAMED_JUNIT3_TEST_CONSTRUCTION\", //$NON-NLS-1$\n"
    "\t\t\t\t\t\t\"The named JUnit 3 constructor contains behavior beyond direct super(name) delegation.\")); //$NON-NLS-1$\n"
    "\t\t\t}\n"
    "\t\t\treturn Optional.of(new Rejection(\"CUSTOM_JUNIT3_CONSTRUCTOR\", //$NON-NLS-1$\n"
    "\t\t\t\t\t\"The hierarchy declares an explicit constructor whose instantiation contract is not represented by ordinary Jupiter discovery.\")); //$NON-NLS-1$\n"
    "\t\t}\n",
)
replace_once(
    harness,
    "\t\tif (\"suite\".equals(name) && isSuiteBuilder(method)) { //$NON-NLS-1$\n"
    "\t\t\tJUnit3SuiteModel.Result model= JUnit3SuiteModel.analyze(method);\n",
    "\t\tif (\"suite\".equals(name) && isSuiteBuilder(method)) { //$NON-NLS-1$\n"
    "\t\t\tif (owner != null && JUnit3LegacyShape.isSelfSuite(method, owner)) {\n"
    "\t\t\t\treturn Optional.empty();\n"
    "\t\t\t}\n"
    "\t\t\tJUnit3SuiteModel.Result model= JUnit3SuiteModel.analyze(method);\n",
)

planner = "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnit3HierarchyPlanner.java"
replace_once(
    planner,
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3MigrationExclusions;\n",
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3LegacyShape;\n"
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3MigrationExclusions;\n"
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SuiteModel;\n",
)
replace_once(planner, "\t\tMap<String, Integer> lifecycleDeclarations= new HashMap<>();\n", "")
replace_once(
    planner,
    "\t\t\t\tJUnit3HarnessSemantics.Rejection harnessRejection=\n"
    "\t\t\t\t\t\tJUnit3HarnessSemantics.rejection(method).orElse(null);\n",
    "\t\t\t\tJUnit3HarnessSemantics.Rejection harnessRejection=\n"
    "\t\t\t\t\t\tJUnit3HarnessSemantics.rejection(method, type.declaration()).orElse(null);\n",
)
replace_once(
    planner,
    "\t\t\t\tString name= method.getName().getIdentifier();\n"
    "\t\t\t\tString bindingKey= methodKey(method);\n"
    "\t\t\t\tif (name.startsWith(\"test\")) { //$NON-NLS-1$\n",
    "\t\t\t\tString name= method.getName().getIdentifier();\n"
    "\t\t\t\tString bindingKey= methodKey(method);\n"
    "\t\t\t\tboolean removableCompatibilityMember= method.isConstructor()\n"
    "\t\t\t\t\t\t&& JUnit3LegacyShape.isRemovableConstructor(method)\n"
    "\t\t\t\t\t\t|| JUnit3SuiteModel.isSuiteBuilder(method)\n"
    "\t\t\t\t\t\t\t\t&& JUnit3LegacyShape.isSelfSuite(method, type.declaration());\n"
    "\t\t\t\tif (removableCompatibilityMember) {\n"
    "\t\t\t\t\tif (bindingKey == null) {\n"
    "\t\t\t\t\t\treturn Classification.rejected(\"UNRESOLVED_JUNIT3_COMPATIBILITY_MEMBER\", //$NON-NLS-1$\n"
    "\t\t\t\t\t\t\t\t\"A proven compatibility constructor or self suite has no stable method binding.\"); //$NON-NLS-1$\n"
    "\t\t\t\t\t}\n"
    "\t\t\t\t\tmethods.add(new MethodMigration(bindingKey, MethodKind.REMOVE_COMPATIBILITY_MEMBER));\n"
    "\t\t\t\t\tcontinue;\n"
    "\t\t\t\t}\n"
    "\t\t\t\tif (name.startsWith(\"test\")) { //$NON-NLS-1$\n",
)
replace_once(
    planner,
    "\t\t\t\t\tint count= lifecycleDeclarations.merge(name, Integer.valueOf(1), Integer::sum).intValue();\n"
    "\t\t\t\t\tif (count > 1) {\n"
    "\t\t\t\t\t\treturn Classification.rejected(\"JUNIT3_LIFECYCLE_OVERRIDE_CHAIN\", //$NON-NLS-1$\n"
    "\t\t\t\t\t\t\t\t\"Lifecycle override chains require explicit semantic migration.\"); //$NON-NLS-1$\n"
    "\t\t\t\t\t}\n",
    "",
)
regex_once(
    planner,
    r"\tprivate static OverrideRemoval plannedOverrideRemoval\(MethodDeclaration method\) \{.*?\n\t\}\n\n\tprivate static boolean hasJUnitAnnotation",
    "\tprivate static OverrideRemoval plannedOverrideRemoval(MethodDeclaration method) {\n"
    "\t\tboolean overrideFound= false;\n"
    "\t\tfor (Object modifier : method.modifiers()) {\n"
    "\t\t\tif (!(modifier instanceof Annotation annotation)) {\n"
    "\t\t\t\tcontinue;\n"
    "\t\t\t}\n"
    "\t\t\tITypeBinding annotationBinding= annotation.resolveTypeBinding();\n"
    "\t\t\tString writtenName= annotation.getTypeName().getFullyQualifiedName();\n"
    "\t\t\tif (annotationBinding != null && !annotationBinding.isRecovered()) {\n"
    "\t\t\t\tif (\"java.lang.Override\".equals(annotationBinding.getQualifiedName())) { //$NON-NLS-1$\n"
    "\t\t\t\t\toverrideFound= true;\n"
    "\t\t\t\t}\n"
    "\t\t\t\tcontinue;\n"
    "\t\t\t}\n"
    "\t\t\tif (\"java.lang.Override\".equals(writtenName)) { //$NON-NLS-1$\n"
    "\t\t\t\toverrideFound= true;\n"
    "\t\t\t} else if (\"Override\".equals(writtenName)) { //$NON-NLS-1$\n"
    "\t\t\t\treturn OverrideRemoval.UNRESOLVED;\n"
    "\t\t\t}\n"
    "\t\t}\n"
    "\t\tif (!overrideFound) {\n"
    "\t\t\treturn OverrideRemoval.KEEP;\n"
    "\t\t}\n"
    "\t\tIMethodBinding methodBinding= method.resolveBinding();\n"
    "\t\tITypeBinding declaring= methodBinding == null ? null : methodBinding.getDeclaringClass();\n"
    "\t\tString name= method.getName().getIdentifier();\n"
    "\t\tfor (ITypeBinding current= declaring == null ? null : declaring.getSuperclass();\n"
    "\t\t\t\tcurrent != null; current= current.getSuperclass()) {\n"
    "\t\t\tfor (IMethodBinding candidate : current.getDeclaredMethods()) {\n"
    "\t\t\t\tif (name.equals(candidate.getName()) && candidate.getParameterTypes().length == 0) {\n"
    "\t\t\t\t\treturn JUNIT3_TEST_CASE.equals(current.getErasure().getQualifiedName())\n"
    "\t\t\t\t\t\t\t? OverrideRemoval.REMOVE : OverrideRemoval.KEEP;\n"
    "\t\t\t\t}\n"
    "\t\t\t}\n"
    "\t\t}\n"
    "\t\treturn OverrideRemoval.UNRESOLVED;\n"
    "\t}\n\n"
    "\tprivate static boolean hasJUnitAnnotation",
)

hint = "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/hints/junit3-hierarchy-to-jupiter.sandbox-hint"
replace_once(
    hint,
    "@id: junit3.planned.beforeEach\n"
    "void $name($params$) :: plannedRole($name, \"JUNIT3_BEFORE_EACH\") && plannedValue($name, \"removeOverride\", false) && !hasOverrideAnnotation($name)\n"
    "=>! addAnnotation(annotation=\"org.junit.jupiter.api.BeforeEach\")\n"
    ";;\n",
    "@id: junit3.planned.beforeEach.inheritedOverride\n"
    "void $name($params$) :: plannedRole($name, \"JUNIT3_BEFORE_EACH\") && plannedValue($name, \"removeOverride\", false) && hasOverrideAnnotation($name)\n"
    "=>! addAnnotation(annotation=\"org.junit.jupiter.api.BeforeEach\")\n"
    ";;\n\n"
    "@id: junit3.planned.beforeEach\n"
    "void $name($params$) :: plannedRole($name, \"JUNIT3_BEFORE_EACH\") && plannedValue($name, \"removeOverride\", false) && !hasOverrideAnnotation($name)\n"
    "=>! addAnnotation(annotation=\"org.junit.jupiter.api.BeforeEach\")\n"
    ";;\n",
)
replace_once(
    hint,
    "@id: junit3.planned.afterEach\n"
    "void $name($params$) :: plannedRole($name, \"JUNIT3_AFTER_EACH\") && plannedValue($name, \"removeOverride\", false) && !hasOverrideAnnotation($name)\n"
    "=>! addAnnotation(annotation=\"org.junit.jupiter.api.AfterEach\")\n"
    ";;\n",
    "@id: junit3.planned.afterEach.inheritedOverride\n"
    "void $name($params$) :: plannedRole($name, \"JUNIT3_AFTER_EACH\") && plannedValue($name, \"removeOverride\", false) && hasOverrideAnnotation($name)\n"
    "=>! addAnnotation(annotation=\"org.junit.jupiter.api.AfterEach\")\n"
    ";;\n\n"
    "@id: junit3.planned.afterEach\n"
    "void $name($params$) :: plannedRole($name, \"JUNIT3_AFTER_EACH\") && plannedValue($name, \"removeOverride\", false) && !hasOverrideAnnotation($name)\n"
    "=>! addAnnotation(annotation=\"org.junit.jupiter.api.AfterEach\")\n"
    ";;\n",
)

plan = "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitMigrationPlan.java"
replace_once(plan, "import org.eclipse.jdt.core.dom.FieldDeclaration;\n",
             "import org.eclipse.jdt.core.dom.ExpressionStatement;\nimport org.eclipse.jdt.core.dom.FieldDeclaration;\n")
replace_once(plan, "import org.eclipse.jdt.core.dom.MethodInvocation;\n",
             "import org.eclipse.jdt.core.dom.MethodInvocation;\nimport org.eclipse.jdt.core.dom.SuperMethodInvocation;\n")
replace_once(
    plan,
    "import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;\n",
    "import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;\n"
    "import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3LegacyShape;\n",
)
replace_once(
    plan,
    "\t\tSemanticRewritePlan.Builder plan= SemanticRewritePlan.builder(\"junit3-hierarchy\"); //$NON-NLS-1$\n",
    "\t\tSet<MethodDeclaration> compatibilityDeclarations= new LinkedHashSet<>();\n"
    "\t\tSet<ExpressionStatement> redundantLifecycleCalls= new LinkedHashSet<>();\n"
    "\t\tfor (TypeMigration planned : plannedTypes) {\n"
    "\t\t\tfor (MethodMigration method : planned.methods()) {\n"
    "\t\t\t\tMethodDeclaration resolved= resolvedMethods.get(method.methodBindingKey());\n"
    "\t\t\t\tif (method.kind() == MethodKind.REMOVE_COMPATIBILITY_MEMBER) {\n"
    "\t\t\t\t\tcompatibilityDeclarations.add(resolved);\n"
    "\t\t\t\t\tcontinue;\n"
    "\t\t\t\t}\n"
    "\t\t\t\tif (method.kind() == MethodKind.BEFORE_EACH || method.kind() == MethodKind.AFTER_EACH) {\n"
    "\t\t\t\t\tresolved.accept(new ASTVisitor() {\n"
    "\t\t\t\t\t\t@Override\n"
    "\t\t\t\t\t\tpublic boolean visit(SuperMethodInvocation invocation) {\n"
    "\t\t\t\t\t\t\tif (JUnit3LegacyShape.isRedundantLifecycleSuperCall(invocation)\n"
    "\t\t\t\t\t\t\t\t\t&& invocation.getParent() instanceof ExpressionStatement statement) {\n"
    "\t\t\t\t\t\t\t\tredundantLifecycleCalls.add(statement);\n"
    "\t\t\t\t\t\t\t}\n"
    "\t\t\t\t\t\t\treturn true;\n"
    "\t\t\t\t\t\t}\n"
    "\t\t\t\t\t});\n"
    "\t\t\t\t}\n"
    "\t\t\t}\n"
    "\t\t}\n"
    "\t\tif (!compatibilityDeclarations.isEmpty() || !redundantLifecycleCalls.isEmpty()) {\n"
    "\t\t\toperations.add(new JUnit3CompatibilityRewriteOperation(compatibilityDeclarations,\n"
    "\t\t\t\t\tredundantLifecycleCalls));\n"
    "\t\t\tnodesProcessed.addAll(compatibilityDeclarations);\n"
    "\t\t\tnodesProcessed.addAll(redundantLifecycleCalls);\n"
    "\t\t}\n\n"
    "\t\tSemanticRewritePlan.Builder plan= SemanticRewritePlan.builder(\"junit3-hierarchy\"); //$NON-NLS-1$\n",
)
replace_once(
    plan,
    "\t\t\tfor (MethodMigration method : planned.methods()) {\n"
    "\t\t\t\tNodeKey key= NodeKey.method(method.methodBindingKey());\n"
    "\t\t\t\tplan.add(key, methodRole(method.kind()));\n",
    "\t\t\tfor (MethodMigration method : planned.methods()) {\n"
    "\t\t\t\tif (method.kind() == MethodKind.REMOVE_COMPATIBILITY_MEMBER) {\n"
    "\t\t\t\t\tcontinue;\n"
    "\t\t\t\t}\n"
    "\t\t\t\tNodeKey key= NodeKey.method(method.methodBindingKey());\n"
    "\t\t\t\tplan.add(key, methodRole(method.kind()));\n",
)
replace_once(
    plan,
    "\t\tcase AFTER_EACH -> ROLE_AFTER_EACH;\n",
    "\t\tcase AFTER_EACH -> ROLE_AFTER_EACH;\n"
    "\t\tcase REMOVE_COMPATIBILITY_MEMBER -> throw new IllegalArgumentException(\n"
    "\t\t\t\t\"Compatibility members are removed by a dedicated planned AST operation.\"); //$NON-NLS-1$\n",
)

Path("qa/apply-junit3-hierarchy-core.py").unlink()
