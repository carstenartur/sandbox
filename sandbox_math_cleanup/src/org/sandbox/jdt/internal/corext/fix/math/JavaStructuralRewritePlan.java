/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/** Verifies and groups Java edits supplied by a source refactoring. It checks bindings and API, not arithmetic. */
public final class JavaStructuralRewritePlan {
    /** A constructor has name {@code <init>} and result {@code void}. Type names use erased Java names. */
    public record Method(String owner, String name, List<String> parameters, String result) {
        public Method {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(name);
            Objects.requireNonNull(result);
            parameters = List.copyOf(parameters);
        }
    }

    /** An expected invocation or method-reference count in a particular caller before and after the edits. */
    public record Invocation(Method caller, Method target, int before, int after) {
        public Invocation {
            Objects.requireNonNull(caller);
            Objects.requireNonNull(target);
            if (before < 0 || after < 0) throw new IllegalArgumentException("NEGATIVE_INVOCATION_COUNT"); //$NON-NLS-1$
        }
    }

    private record Body(String syntax, List<Method> calls, Map<String, List<String>> references) { }
    private record Member(String owner, String name, String declaration) { }
    private record Name(String owner, String name) { }
    private record Snapshot(Map<Method, Body> methods, Map<String, Body> initializers, Set<Member> api) { }
    private record BinaryInput(Path root, String sha256) { }

    private final Map<String, String> original;
    private final Map<String, String> replacement;
    private final Map<String, JavaSourceRewritePlan> edits;
    private final List<BinaryInput> binaries;
    private final List<Invocation> invocations;

    private JavaStructuralRewritePlan(Map<String, String> original, Map<String, String> replacement, List<BinaryInput> binaries,
            List<Invocation> invocations) {
        this.original = original;
        this.replacement = replacement;
        this.binaries = binaries;
        this.invocations = invocations;
        var changed = new LinkedHashMap<String, JavaSourceRewritePlan>();
        original.keySet().stream().sorted().forEach(path -> {
            if (!original.get(path).equals(replacement.get(path)))
                changed.put(path, edit(path, original.get(path), replacement.get(path)));
        });
        edits = java.util.Collections.unmodifiableMap(changed);
    }

    /**
     * Resolve both complete sets in one compiler environment. Include participating sources and
     * source dependencies in the maps, using paths relative to their source root. Existing external
     * binaries can be supplied on the classpath. Applying this plan requires the same source snapshot.
     */
    public static JavaStructuralRewritePlan verify(Map<String, String> original, Map<String, String> replacement,
            List<String> classpath, int targetJava, List<Invocation> expected, IProgressMonitor monitor) {
        var before = Map.copyOf(original);
        var after = Map.copyOf(replacement);
        var dependencyPaths = List.copyOf(classpath);
        var invocations = List.copyOf(expected);
        if (before.isEmpty() || !before.keySet().equals(after.keySet()))
            throw new IllegalArgumentException("STRUCTURAL_SOURCE_SET_DIFFERS"); //$NON-NLS-1$
        for (String key : before.keySet()) verifyPath(key);
        var binaryInputs = dependencyPaths.stream().map(Path::of).map(JavaStructuralRewritePlan::binaryInput).toList();
        // JDT source binding keys can include the source path. Keep it stable across both compilations.
        try (var temporary = new TemporarySources()) {
            var old = snapshot(before, temporary.root, dependencyPaths, targetJava, monitor);
            var changed = snapshot(after, temporary.root, dependencyPaths, targetJava, monitor);
            verifySnapshots(old, changed, invocations, monitor);
            requireCurrentBinaries(binaryInputs);
            return new JavaStructuralRewritePlan(before, after, binaryInputs, invocations);
        } catch (IOException inaccessible) {
            throw new UncheckedIOException(inaccessible);
        }
    }

    /** Verify against the real Java project's compiler options, classpath and private working copies. */
    public static JavaStructuralRewritePlan verifyWorkspace(Map<String, ICompilationUnit> units, Map<String, String> replacement,
            List<Invocation> expected, IProgressMonitor monitor) throws JavaModelException {
        return verifyWorkspace(units, replacement, expected, monitor, false);
    }

    private static JavaStructuralRewritePlan verifyWorkspace(Map<String, ICompilationUnit> units, Map<String, String> replacement,
            List<Invocation> expected, IProgressMonitor monitor, boolean undo) throws JavaModelException {
        var scope = Map.copyOf(units);
        var after = Map.copyOf(replacement);
        if (scope.isEmpty() || !scope.keySet().equals(after.keySet()) || new HashSet<>(scope.values()).size() != scope.size())
            throw new IllegalArgumentException("STRUCTURAL_SOURCE_SET_DIFFERS"); //$NON-NLS-1$
        var project = scope.values().iterator().next().getJavaProject();
        for (var other : project.getJavaModel().getJavaProjects()) {
            checkCancellation(monitor);
            if (Arrays.asList(other.getRequiredProjectNames()).contains(project.getElementName()))
                throw new IllegalArgumentException("STRUCTURAL_DEPENDENT_PROJECT: " + other.getElementName()); //$NON-NLS-1$
        }
        var before = new LinkedHashMap<String, String>();
        for (var source : scope.entrySet()) {
            checkCancellation(monitor);
            verifyPath(source.getKey());
            if (!source.getValue().exists() || source.getValue().hasUnsavedChanges() || !project.equals(source.getValue().getJavaProject()))
                throw new IllegalArgumentException("STRUCTURAL_WORKSPACE_SCOPE_DIFFERS"); //$NON-NLS-1$
            before.put(source.getKey(), source.getValue().getSource());
        }
        // An unedited source can still lose a call or resolve to a different overload.
        var complete = new LinkedHashMap<>(scope);
        var current = new LinkedHashMap<>(before);
        var edited = new LinkedHashMap<>(after);
        var primary = new HashSet<ICompilationUnit>();
        scope.values().forEach(unit -> primary.add(unit.getPrimary()));
        for (var fragment : project.getPackageFragments()) {
            if (fragment.getKind() != IPackageFragmentRoot.K_SOURCE || !fragment.getJavaProject().equals(project)) continue;
            for (var unit : fragment.getCompilationUnits()) {
                checkCancellation(monitor);
                if (!primary.add(unit.getPrimary())) continue;
                if (!unit.exists() || unit.hasUnsavedChanges())
                    throw new IllegalArgumentException("STRUCTURAL_WORKSPACE_SCOPE_DIFFERS"); //$NON-NLS-1$
                String key = unit.getPath().toPortableString();
                String source = unit.getSource();
                complete.put(key, unit);
                current.put(key, source);
                edited.put(key, source);
            }
        }
        var old = workspaceSnapshot(complete, current, monitor);
        var changed = workspaceSnapshot(complete, edited, monitor);
        var calls = List.copyOf(expected);
        verifySnapshots(undo ? changed : old, undo ? old : changed, calls, monitor);
        for (var source : complete.entrySet()) {
            if (!source.getValue().exists() || source.getValue().hasUnsavedChanges()
                    || !current.get(source.getKey()).equals(source.getValue().getSource()))
                throw new IllegalArgumentException("STALE_STRUCTURAL_REWRITE"); //$NON-NLS-1$
        }
        return new JavaStructuralRewritePlan(Map.copyOf(before), after, List.of(), calls);
    }

    /** Attach an already verified proposal to a real project without accepting any replacement source. */
    public JavaStructuralRewritePlan rebindWorkspace(Map<String, ICompilationUnit> units, IProgressMonitor monitor)
            throws JavaModelException {
        requireCurrentBinaries(binaries);
        var workspace = verifyWorkspace(units, replacement, invocations, monitor);
        if (!original.equals(workspace.original))
            throw new IllegalArgumentException("STALE_STRUCTURAL_REWRITE"); //$NON-NLS-1$
        requireCurrentBinaries(binaries);
        return workspace;
    }

    /** Recompile the inverse against the current callers, preserving the direction of the original contract. */
    public void verifyWorkspaceUndo(Map<String, ICompilationUnit> units, IProgressMonitor monitor) throws JavaModelException {
        requireCurrentBinaries(binaries);
        var workspace = verifyWorkspace(units, original, invocations, monitor, true);
        if (!replacement.equals(workspace.original))
            throw new IllegalArgumentException("STALE_STRUCTURAL_UNDO"); //$NON-NLS-1$
        requireCurrentBinaries(binaries);
    }

    private static void verifySnapshots(Snapshot old, Snapshot changed, List<Invocation> invocations, IProgressMonitor monitor) {
        verifyPublicSurface(old, changed);
        verifyRetainedBindings(old.methods(), changed.methods());
        verifyMigratedBindings(old.methods(), changed.methods());
        verifyRetainedBindings(old.initializers(), changed.initializers());
        for (var invocation : invocations) {
            checkCancellation(monitor);
            if (callCount(old, invocation) != invocation.before() || callCount(changed, invocation) != invocation.after())
                throw new IllegalArgumentException("STRUCTURAL_INVOCATION_DIFFERS: " + invocation); //$NON-NLS-1$
        }
        checkCancellation(monitor);
    }

    public List<String> changedUnits() { return List.copyOf(edits.keySet()); }
    public Set<String> sourceUnits() { return original.keySet(); }

    /** Capture every edit only after the entire current snapshot, including unedited units, matches. */
    public Map<String, TextEdit> newEdits(Map<String, String> current) {
        if (!original.equals(current)) throw new IllegalArgumentException("STALE_STRUCTURAL_REWRITE"); //$NON-NLS-1$
        requireCurrentBinaries(binaries);
        var result = new LinkedHashMap<String, TextEdit>();
        edits.forEach((key, plan) -> result.put(key, plan.newEdit(original.get(key))));
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Apply to a detached source snapshot. A failure publishes no partial edit. */
    public Map<String, String> apply(Map<String, String> current) {
        var textEdits = newEdits(current);
        var result = new HashMap<>(original);
        for (var edit : textEdits.entrySet()) {
            var document = new Document(original.get(edit.getKey()));
            try {
                edit.getValue().apply(document);
            } catch (BadLocationException invalid) {
                throw new IllegalArgumentException("STRUCTURAL_REWRITE_RANGE", invalid); //$NON-NLS-1$
            }
            result.put(edit.getKey(), document.get());
        }
        return Map.copyOf(result);
    }

    public Map<String, String> undo(Map<String, String> current) {
        if (!replacement.equals(current)) throw new IllegalArgumentException("STALE_STRUCTURAL_UNDO"); //$NON-NLS-1$
        requireCurrentBinaries(binaries);
        return original;
    }

    private static JavaSourceRewritePlan edit(String path, String before, String after) {
        int start = 0, beforeEnd = before.length(), afterEnd = after.length();
        while (start < beforeEnd && start < afterEnd && before.charAt(start) == after.charAt(start)) start++;
        while (beforeEnd > start && afterEnd > start && before.charAt(beforeEnd - 1) == after.charAt(afterEnd - 1)) {
            beforeEnd--;
            afterEnd--;
        }
        return new JavaSourceRewritePlan(path, JavaComputationTask.hash(before), before.length(), 0, before.length(),
            List.of(), List.of(), Map.of(), List.of(new JavaSourceRewritePlan.Change(start, beforeEnd - start,
                JavaComputationTask.hash(before.substring(start, beforeEnd)), after.substring(start, afterEnd))));
    }

    private static int callCount(Snapshot snapshot, Invocation invocation) {
        var body = snapshot.methods().get(invocation.caller());
        if (body == null) return 0;
        return Math.toIntExact(body.calls().stream().filter(invocation.target()::equals).count());
    }

    private static <K> void verifyRetainedBindings(Map<K, Body> before, Map<K, Body> after) {
        for (var previous : before.entrySet()) {
            var current = after.get(previous.getKey());
            if (current != null) verifyRetainedBody(previous.getKey(), previous.getValue(), current);
        }
    }

    private static void verifyMigratedBindings(Map<Method, Body> before, Map<Method, Body> after) {
        var additions = new HashMap<Name, List<Body>>();
        after.forEach((method, body) -> {
            if (!before.containsKey(method))
                additions.computeIfAbsent(new Name(method.owner(), method.name()), key -> new ArrayList<>()).add(body);
        });
        before.forEach((method, body) -> {
            if (!after.containsKey(method))
                for (var replacement : additions.getOrDefault(new Name(method.owner(), method.name()), List.of()))
                    verifyRetainedBody(method, body, replacement);
        });
    }

    private static void verifyRetainedBody(Object declaration, Body before, Body after) {
        if (before.syntax().equals(after.syntax()) && !before.calls().equals(after.calls()))
            throw new IllegalArgumentException("RETAINED_JAVA_BINDING_DIFFERS: " + declaration); //$NON-NLS-1$
        for (var reference : before.references().entrySet()) {
            var retained = after.references().get(reference.getKey());
            if (retained == null || retained.equals(reference.getValue())) continue;
            // A text match cannot disambiguate changed counts of otherwise identical references.
            var bindings = new HashSet<>(reference.getValue());
            bindings.addAll(retained);
            if (bindings.size() != 1)
                throw new IllegalArgumentException("RETAINED_JAVA_BINDING_DIFFERS: " + declaration + " " //$NON-NLS-1$ //$NON-NLS-2$
                    + reference.getKey());
        }
    }

    private static void verifyPublicSurface(Snapshot before, Snapshot after) {
        if (!after.api().containsAll(before.api())) throw new IllegalArgumentException("EXTERNAL_JAVA_SIGNATURE_DIFFERS"); //$NON-NLS-1$
        var names = new HashSet<Name>();
        for (var member : before.api()) names.add(new Name(member.owner(), member.name()));
        for (var member : after.api()) if (!before.api().contains(member) && names.contains(new Name(member.owner(), member.name())))
            throw new IllegalArgumentException("EXTERNAL_JAVA_OVERLOAD_ADDED"); //$NON-NLS-1$
    }

    private static Snapshot snapshot(Map<String, String> input, Path sourceRoot, List<String> classpath, int target,
            IProgressMonitor monitor) throws IOException {
        var sources = new ArrayList<String>();
        for (String key : input.keySet().stream().sorted().toList()) {
            checkCancellation(monitor);
            Path file = sourceRoot.resolve(key);
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            Files.writeString(file, input.get(key));
            sources.add(file.toString());
        }
        var parser = ASTParser.newParser(AST.getJLSLatest());
        var options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(target == 8 ? "1.8" : Integer.toString(target), options); //$NON-NLS-1$
        parser.setCompilerOptions(options);
        parser.setResolveBindings(true);
        parser.setEnvironment(classpath.toArray(String[]::new), new String[] { sourceRoot.toString() },
            new String[] { "UTF-8" }, true); //$NON-NLS-1$
        var units = new ArrayList<CompilationUnit>();
        String[] encodings = new String[sources.size()];
        Arrays.fill(encodings, "UTF-8"); //$NON-NLS-1$
        parser.createASTs(sources.toArray(String[]::new), encodings, new String[0], new FileASTRequestor() {
            @Override public void acceptAST(String path, CompilationUnit ast) {
                checkCancellation(monitor);
                for (var problem : ast.getProblems()) if (problem.isError())
                    throw new IllegalArgumentException("STRUCTURAL_JAVA_ERROR: " + sourceRoot.relativize(Path.of(path)) //$NON-NLS-1$
                        + ":" + problem.getSourceLineNumber() + " " + problem.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
                units.add(ast);
            }
        }, monitor);
        if (units.size() != input.size()) throw new IllegalArgumentException("STRUCTURAL_INCOMPLETE_COMPILATION"); //$NON-NLS-1$
        return describe(units, monitor);
    }

    private static Snapshot workspaceSnapshot(Map<String, ICompilationUnit> scope, Map<String, String> sources,
            IProgressMonitor monitor) throws JavaModelException {
        var owner = new WorkingCopyOwner() { };
        var copies = new ArrayList<ICompilationUnit>();
        try {
            for (String name : sources.keySet().stream().sorted().toList()) {
                checkCancellation(monitor);
                var copy = scope.get(name).getPrimary().getWorkingCopy(owner, monitor);
                copies.add(copy);
                copy.getBuffer().setContents(sources.get(name));
            }
            var parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setProject(copies.getFirst().getJavaProject());
            parser.setWorkingCopyOwner(owner);
            parser.setResolveBindings(true);
            var parsed = new ArrayList<CompilationUnit>();
            var errors = new ArrayList<String>();
            parser.createASTs(copies.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
                @Override public void acceptAST(ICompilationUnit unit, CompilationUnit ast) {
                    checkCancellation(monitor);
                    for (var problem : ast.getProblems()) if (problem.isError())
                        errors.add("STRUCTURAL_JAVA_ERROR: " + unit.getElementName() //$NON-NLS-1$
                            + ":" + problem.getSourceLineNumber() + " " + problem.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
                    parsed.add(ast);
                }
            }, monitor);
            if (!errors.isEmpty()) throw new IllegalArgumentException(errors.getFirst());
            if (parsed.size() != scope.size()) throw new IllegalArgumentException("STRUCTURAL_INCOMPLETE_COMPILATION"); //$NON-NLS-1$
            return describe(parsed, monitor);
        } finally {
            for (var copy : copies) copy.discardWorkingCopy();
        }
    }

    private static Snapshot describe(List<CompilationUnit> units, IProgressMonitor monitor) {
        var methods = new HashMap<Method, Body>();
        var initializers = new HashMap<String, Body>();
        var api = new HashSet<Member>();
        for (var unit : units) unit.accept(new ASTVisitor() {
            private final Map<String, Integer> initializerCounts = new HashMap<>();
            @Override public void preVisit(ASTNode node) { checkCancellation(monitor); }
            @Override public boolean visit(TypeDeclaration node) { recordSurface(node.resolveBinding(), api); return true; }
            @Override public boolean visit(EnumDeclaration node) { recordSurface(node.resolveBinding(), api); return true; }
            @Override public boolean visit(RecordDeclaration node) { recordSurface(node.resolveBinding(), api); return true; }
            @Override public boolean visit(AnnotationTypeDeclaration node) { recordSurface(node.resolveBinding(), api); return true; }
            @Override public boolean visit(MethodDeclaration node) {
                if (node.getBody() != null) {
                    var method = symbol(node.resolveBinding());
                    methods.put(method, describeBody(node.getBody()));
                }
                return true;
            }
            @Override public boolean visit(VariableDeclarationFragment node) {
                if (node.getParent() instanceof FieldDeclaration && node.getInitializer() != null) {
                    var binding = node.resolveBinding();
                    recordInitializer(type(binding.getDeclaringClass()) + "." + binding.getName(), node.getInitializer()); //$NON-NLS-1$
                }
                return true;
            }
            @Override public boolean visit(Initializer node) {
                if (node.getParent() instanceof AbstractTypeDeclaration type) {
                    String owner = type(type.resolveBinding());
                    String kind = owner + (Modifier.isStatic(node.getModifiers()) ? "#static" : "#instance"); //$NON-NLS-1$ //$NON-NLS-2$
                    String key = kind + JavaComputationTask.hash(node.getBody().toString());
                    recordInitializer(key + initializerCounts.merge(key, 1, Integer::sum), node.getBody());
                }
                return true;
            }
            private void recordInitializer(String key, ASTNode node) {
                initializers.put(key, describeBody(node));
            }
        });
        return new Snapshot(Map.copyOf(methods), Map.copyOf(initializers), Set.copyOf(api));
    }

    private static void recordSurface(ITypeBinding binding, Set<Member> api) {
        if (binding == null || binding.isRecovered()) throw new IllegalArgumentException("UNRESOLVED_JAVA_TYPE"); //$NON-NLS-1$
        if (!external(binding.getModifiers())) return;
        for (var enclosing = binding; enclosing != null; enclosing = enclosing.getDeclaringClass())
            if (enclosing.isLocal() || enclosing.isAnonymous()) return;
        String owner = type(binding);
        var parents = new ArrayList<ITypeBinding>(Arrays.asList(binding.getInterfaces()));
        if (binding.getSuperclass() != null) parents.add(binding.getSuperclass());
        api.add(new Member(owner, "#type", binding.getKey() + " " + binding.getModifiers() + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + parents.stream().map(ITypeBinding::getKey).toList() + typeParameters(binding.getTypeParameters())));
        var visited = new HashSet<String>();
        for (var parent : parents) recordInheritedSurface(owner, parent, api, visited);
        recordMembers(owner, binding, api, true);
    }

    private static void recordMembers(String owner, ITypeBinding binding, Set<Member> api, boolean constructors) {
        for (var method : binding.getDeclaredMethods()) if (external(method.getModifiers()) && (constructors || !method.isConstructor())) {
            var shape = symbol(method);
            var thrown = Arrays.stream(method.getExceptionTypes()).map(ITypeBinding::getKey).toList();
            api.add(new Member(owner, shape.name(), method.getKey() + " " + method.getModifiers() + " " + thrown //$NON-NLS-1$ //$NON-NLS-2$
                + typeParameters(method.getTypeParameters()) + method.getReturnType().getKey()
                + Arrays.stream(method.getParameterTypes()).map(ITypeBinding::getKey).toList() + method.isVarargs()));
        }
        for (var field : binding.getDeclaredFields()) if (external(field.getModifiers()))
            api.add(new Member(owner, field.getName(), field.getKey() + " " + field.getType().getKey() + " " //$NON-NLS-1$ //$NON-NLS-2$
                + field.getModifiers() + " " + field.getConstantValue())); //$NON-NLS-1$
        for (var nested : binding.getDeclaredTypes()) if (external(nested.getModifiers()))
            api.add(new Member(owner, nested.getName(), nested.getKey() + " " + nested.getModifiers())); //$NON-NLS-1$
    }

    private static List<List<String>> typeParameters(ITypeBinding[] parameters) {
        return Arrays.stream(parameters).map(parameter -> Arrays.stream(parameter.getTypeBounds()).map(ITypeBinding::getKey).toList()).toList();
    }

    private static void recordInheritedSurface(String owner, ITypeBinding parent, Set<Member> api, Set<String> visited) {
        if (parent == null || !visited.add(parent.getKey())) return;
        recordMembers(owner, parent, api, false);
        recordInheritedSurface(owner, parent.getSuperclass(), api, visited);
        for (var iface : parent.getInterfaces()) recordInheritedSurface(owner, iface, api, visited);
    }

    private static Body describeBody(ASTNode root) {
        var calls = new ArrayList<Method>();
        var references = new HashMap<String, List<String>>();
        root.accept(new ASTVisitor() {
            private void reference(String kind, ASTNode node, String binding) {
                references.computeIfAbsent(kind + node, ignored -> new ArrayList<>()).add(binding);
            }
            private boolean add(ASTNode node, IMethodBinding binding) {
                calls.add(symbol(binding));
                reference("call:", node, binding.getMethodDeclaration().getKey()); //$NON-NLS-1$
                return true;
            }
            @Override public boolean visit(MethodDeclaration node) { return false; }
            @Override public boolean visit(AnonymousClassDeclaration node) { return false; }
            @Override public boolean visit(SimpleName node) {
                if (node.isDeclaration()) return true;
                var binding = node.resolveBinding();
                if (binding instanceof IVariableBinding variable) {
                    var position = node.getLocationInParent();
                    boolean qualified = position == FieldAccess.NAME_PROPERTY || position == SuperFieldAccess.NAME_PROPERTY
                        || position == QualifiedName.NAME_PROPERTY;
                    reference("variable:", qualified ? node.getParent() : node, //$NON-NLS-1$
                        variable.isField() ? variable.getVariableDeclaration().getKey() : "local"); //$NON-NLS-1$
                } else if (binding instanceof ITypeBinding type) reference("type:", node, type.getKey()); //$NON-NLS-1$
                return true;
            }
            @Override public boolean visit(MethodInvocation node) { return add(node, node.resolveMethodBinding()); }
            @Override public boolean visit(SuperMethodInvocation node) { return add(node, node.resolveMethodBinding()); }
            @Override public boolean visit(ClassInstanceCreation node) { return add(node, node.resolveConstructorBinding()); }
            @Override public boolean visit(ConstructorInvocation node) { return add(node, node.resolveConstructorBinding()); }
            @Override public boolean visit(SuperConstructorInvocation node) { return add(node, node.resolveConstructorBinding()); }
            @Override public boolean visit(ExpressionMethodReference node) { return add(node, node.resolveMethodBinding()); }
            @Override public boolean visit(TypeMethodReference node) { return add(node, node.resolveMethodBinding()); }
            @Override public boolean visit(SuperMethodReference node) { return add(node, node.resolveMethodBinding()); }
            @Override public boolean visit(CreationReference node) {
                return node.getType().isArrayType() || add(node, node.resolveMethodBinding());
            }
        });
        return new Body(root.toString(), List.copyOf(calls), references);
    }

    private static Method symbol(IMethodBinding binding) {
        if (binding == null || binding.isRecovered()) throw new IllegalArgumentException("UNRESOLVED_JAVA_INVOCATION"); //$NON-NLS-1$
        var declaration = binding.getMethodDeclaration();
        return new Method(type(declaration.getDeclaringClass()), declaration.isConstructor() ? "<init>" : declaration.getName(), //$NON-NLS-1$
            Arrays.stream(declaration.getParameterTypes()).map(JavaStructuralRewritePlan::type).toList(),
            declaration.isConstructor() ? "void" : type(declaration.getReturnType())); //$NON-NLS-1$
    }

    private static String type(ITypeBinding binding) {
        if (binding == null) return ""; //$NON-NLS-1$
        if (binding.isArray()) return type(binding.getElementType()) + "[]".repeat(binding.getDimensions()); //$NON-NLS-1$
        if (binding.isPrimitive()) return binding.getName();
        var erased = binding.getErasure();
        var binary = erased.getBinaryName();
        return binary == null ? erased.getQualifiedName() : binary;
    }

    private static boolean external(int modifiers) { return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers); }

    private static void verifyPath(String key) {
        Path path = Path.of(key);
        if (path.isAbsolute() || !key.equals(path.normalize().toString().replace('\\', '/'))
                || key.startsWith("../") || !key.endsWith(".java")) //$NON-NLS-1$ //$NON-NLS-2$
            throw new IllegalArgumentException("INVALID_JAVA_SOURCE_PATH"); //$NON-NLS-1$
    }

    private static void checkCancellation(IProgressMonitor monitor) {
        if (monitor != null && monitor.isCanceled()) throw new OperationCanceledException();
    }

    private static void requireCurrentBinaries(List<BinaryInput> inputs) {
        for (var original : inputs) if (!original.equals(binaryInput(original.root())))
            throw new IllegalArgumentException("STALE_STRUCTURAL_CLASSPATH"); //$NON-NLS-1$
    }

    private static BinaryInput binaryInput(Path entry) {
        Path root = entry.toAbsolutePath().normalize();
        try {
            Path real = root.toRealPath();
            var whole = sha256();
            whole.update(real.toString().getBytes(StandardCharsets.UTF_8));
            whole.update((byte)0);
            if (Files.isDirectory(real)) {
                try (var files = Files.walk(real, FileVisitOption.FOLLOW_LINKS)) {
                    for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                        whole.update(real.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                        whole.update((byte)0);
                        whole.update(fileDigest(file));
                    }
                }
            } else {
                whole.update(fileDigest(real));
            }
            return new BinaryInput(root, HexFormat.of().formatHex(whole.digest()));
        } catch (IOException | UncheckedIOException unavailable) {
            throw new IllegalArgumentException("UNAVAILABLE_STRUCTURAL_CLASSPATH: " + root, unavailable); //$NON-NLS-1$
        }
    }

    private static byte[] fileDigest(Path file) throws IOException {
        var digest = sha256();
        try (var src = new DigestInputStream(Files.newInputStream(file), digest)) {
            src.transferTo(OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static final class TemporarySources implements AutoCloseable {
        final Path root;
        TemporarySources() throws IOException { root = Files.createTempDirectory("sandbox-structural"); } //$NON-NLS-1$
        @Override public void close() throws IOException {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
