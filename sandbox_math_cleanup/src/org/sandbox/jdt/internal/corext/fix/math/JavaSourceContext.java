/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

/** Request-local source facts. Never executes project code or assumes class initialization is complete. */
public final class JavaSourceContext implements AutoCloseable {
    private static final String PROPERTY = JavaSourceContext.class.getName();
    private static final String BIG_INTEGER = "java.math.BigInteger"; //$NON-NLS-1$
    private static final int MAX_UNITS = 24, MAX_DEPTH = 32, MAX_SOURCE_CHARS = 1_000_000;
    private final CompilationUnit root;
    private final MathematicalAnalysis.SourceEnvironment environment;
    private final IProgressMonitor monitor;
    private final int targetJava;
    private final long allowance;
    private final Map<String, Unit> units = new HashMap<>();
    private final Map<String, Optional<BigInteger>> values = new HashMap<>();
    private final Map<String, Dependency> dependencies = new LinkedHashMap<>();
    private long work, accountedWork;
    private int sourceChars;

    private JavaSourceContext(CompilationUnit root, String source, MathematicalAnalysis.SourceEnvironment environment,
            IProgressMonitor monitor, MathCleanUpOptions options) {
        this.root = root; this.environment = environment; this.monitor = monitor; this.targetJava = options.targetJava();
        this.allowance = Math.min(32_000, Math.max(1, options.workBudget() / 8));
        // No project-dependent value is retained across requests, or in the mathematical AST.
        root.setProperty(PROPERTY, this);
    }
    static JavaSourceContext open(CompilationUnit root, String source, MathematicalAnalysis.SourceEnvironment environment,
            IProgressMonitor monitor, MathCleanUpOptions options) {
        if (root.getProperty(PROPERTY) != null) throw new IllegalArgumentException("SOURCE_CONTEXT_ALREADY_ACTIVE"); //$NON-NLS-1$
        return new JavaSourceContext(root, source, environment, monitor, options);
    }
    @Override public void close() { root.setProperty(PROPERTY, null); }
    long takeWork() { long amount = work - accountedWork; accountedWork = work; return amount; }
    Checkpoint checkpoint() { return dependencies.isEmpty() ? Checkpoint.EMPTY : new Checkpoint(List.copyOf(dependencies.values())); }

    /** Checks are immutable and request-bound; they are not serialized as executable report state. */
    public static final class Checkpoint {
        public static final Checkpoint EMPTY = new Checkpoint(List.of());
        private final List<Dependency> dependencies;
        private Checkpoint(List<Dependency> dependencies) { this.dependencies = List.copyOf(dependencies); }
        static Checkpoint detached(List<Receipt> receipts) {
            return new Checkpoint(receipts.stream().map(receipt -> new Dependency(receipt.identity(), receipt.sha256(),
                () -> { throw new IOException("SOURCE_CONTEXT_MUST_BE_REBUILT"); })).toList()); //$NON-NLS-1$
        }
        public boolean current() {
            for (var dependency : dependencies) {
                try { if (!dependency.sha256().equals(hash(dependency.read().get()))) return false; }
                catch (Exception unavailable) { return false; }
            }
            return true;
        }
        public void requireCurrent() {
            if (!current()) throw new IllegalArgumentException("SOURCE_DEPENDENCY_CHANGED"); //$NON-NLS-1$
        }
        public List<Receipt> receipts() {
            return dependencies.stream().map(d -> new Receipt(d.identity(), d.sha256())).toList();
        }
    }
    public record Receipt(String identity, String sha256) { }
    private interface Read { String get() throws Exception; }
    private record Dependency(String identity, String sha256, Read read) { }
    private record Unit(CompilationUnit ast, String identity, String sha256) { }
    record Fact(BigInteger value, String provenance) { }

    static Map<String, Fact> proveReceivers(CompilationUnit unit, JavaComputationRegion region, Map<String,Boolean> requirements) {
        if (!(unit.getProperty(PROPERTY) instanceof JavaSourceContext context)) return Map.of();
        var facts = new HashMap<String, Fact>();
        ASTNode selected = NodeFinder.perform(unit, region.start(), region.length());
        if (selected == null) return Map.of();
        var bindings = new HashMap<String, IVariableBinding>();
        var conflicts = new HashSet<String>();
        try { selected.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                context.charge(1);
                return node.getStartPosition() < (long)region.start() + region.length()
                    && (long)node.getStartPosition() + node.getLength() > region.start();
            }
            @Override public boolean visit(SimpleName name) {
                if (!requirements.containsKey(name.getIdentifier()) || name.isDeclaration()) return true;
                if (name.resolveBinding() instanceof IVariableBinding binding && binding.isField()) {
                    var old = bindings.putIfAbsent(name.getIdentifier(), binding.getVariableDeclaration());
                    if (old != null && !old.isEqualTo(binding.getVariableDeclaration())) conflicts.add(name.getIdentifier());
                } else conflicts.add(name.getIdentifier());
                return true;
            }
        }); } catch (Unavailable unavailable) { return Map.of(); }
        conflicts.forEach(bindings::remove);
        for (var entry : bindings.entrySet()) {
            var binding = entry.getValue();
            try {
                // Only own static fields already admitted by the Java extractor. Do not move foreign initialization.
                if (!(unit.findDeclaringNode(binding) instanceof VariableDeclarationFragment)
                        || !Modifier.isStatic(binding.getModifiers()) || !Modifier.isFinal(binding.getModifiers())) continue;
                if (!context.observedNonnull(binding, NodeFinder.perform(unit, region.start(), 1), region.start())) continue;
                var value = context.constant(binding, new HashSet<>(), 0);
                if (value.isEmpty() || value.get().abs().bitLength() >= 4096
                        || Boolean.TRUE.equals(requirements.get(entry.getKey())) && value.get().signum() <= 0) continue;
                String provenance = "source-defined exact BigInteger, conditioned on an original completed nonnull use; " //$NON-NLS-1$
                    + context.checkpoint().receipts();
                facts.put(entry.getKey(), new Fact(value.get(), provenance));
            } catch (Unavailable unavailable) { /* Missing proof retains the existing full receiver guard. */ }
        }
        return Map.copyOf(facts);
    }

    /** A static alias may observe null during initialization. This proves only its non-null value alternative. */
    private Optional<BigInteger> constant(IVariableBinding field, Set<String> active, int depth) {
        charge(1);
        String key = field.getVariableDeclaration().getKey();
        if (depth > MAX_DEPTH || key == null || !constantField(field)) return Optional.empty();
        if (values.containsKey(key)) return values.get(key);
        if (!active.add(key)) return Optional.empty();
        Optional<BigInteger> value;
        try {
            CompilationUnit ast = definitionUnit(field);
            ASTNode declaration = ast == null ? null : ast.findDeclaringNode(key);
            value = declaration instanceof VariableDeclarationFragment fragment && constantField(fragment.resolveBinding())
                && key.equals(fragment.resolveBinding().getVariableDeclaration().getKey())
                ? expression(fragment.getInitializer(), active, depth + 1) : Optional.empty();
        } finally { active.remove(key); }
        values.put(key, value);
        return value;
    }
    /** Binding keys survive modifier changes; both the caller and the fresh definition must qualify. */
    private static boolean constantField(IVariableBinding field) {
        return field != null && !field.isRecovered() && field.isField()
            && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())
            && !field.getType().isRecovered() && BIG_INTEGER.equals(field.getType().getQualifiedName());
    }
    private Optional<BigInteger> expression(Expression expression, Set<String> active, int depth) {
        charge(1);
        if (depth > MAX_DEPTH || expression == null) return Optional.empty();
        expression = unwrap(expression);
        if (expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding field) {
            if (name instanceof QualifiedName qualified && !(qualified.getQualifier().resolveBinding() instanceof ITypeBinding))
                return Optional.empty();
            if (BIG_INTEGER.equals(field.getDeclaringClass() == null ? null : field.getDeclaringClass().getQualifiedName())) {
                return switch (field.getName()) {
                    case "ZERO" -> Optional.of(BigInteger.ZERO); //$NON-NLS-1$
                    case "ONE" -> Optional.of(BigInteger.ONE); //$NON-NLS-1$
                    case "TWO" -> Optional.of(BigInteger.TWO); //$NON-NLS-1$
                    case "TEN" -> Optional.of(BigInteger.TEN); //$NON-NLS-1$
                    default -> Optional.empty();
                };
            }
            return constant(field, active, depth + 1);
        }
        if (expression instanceof ClassInstanceCreation creation && creation.getAnonymousClassDeclaration() == null
                && creation.resolveConstructorBinding() != null && !creation.resolveConstructorBinding().isRecovered()
                && BIG_INTEGER.equals(creation.resolveConstructorBinding().getDeclaringClass().getQualifiedName())
                && (creation.arguments().size() == 1 || creation.arguments().size() == 2)
                && creation.arguments().getFirst() instanceof StringLiteral text && text.getLiteralValue().length() <= 1234) {
            int radix = 10;
            if (creation.arguments().size() == 2) {
                Object argument = ((Expression)creation.arguments().get(1)).resolveConstantExpressionValue();
                if (!(creation.arguments().get(1) instanceof NumberLiteral) || !(argument instanceof Integer value)) return Optional.empty();
                radix = value;
            }
            try { return bounded(new BigInteger(text.getLiteralValue(), radix)); }
            catch (NumberFormatException invalid) { return Optional.empty(); }
        }
        if (expression instanceof MethodInvocation invocation && invocation.arguments().size() == 1
                && invocation.resolveMethodBinding() != null && !invocation.resolveMethodBinding().isRecovered()
                && Modifier.isStatic(invocation.resolveMethodBinding().getModifiers())
                && BIG_INTEGER.equals(invocation.resolveMethodBinding().getDeclaringClass().getQualifiedName())
                && "valueOf".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
                && invocation.getExpression() instanceof Name qualifier && qualifier.resolveBinding() instanceof ITypeBinding) {
            Expression argument = (Expression)invocation.arguments().getFirst();
            if (!(argument instanceof NumberLiteral || argument instanceof PrefixExpression prefix && prefix.getOperand() instanceof NumberLiteral))
                return Optional.empty();
            Object value = argument.resolveConstantExpressionValue();
            if (value instanceof Integer || value instanceof Long) return bounded(BigInteger.valueOf(((Number)value).longValue()));
        }
        // No arbitrary methods, static initializer execution, reflection, getters or unbounded arithmetic.
        return Optional.empty();
    }
    private Optional<BigInteger> bounded(BigInteger value) {
        return value.abs().bitLength() <= 4096 ? Optional.of(value) : Optional.empty();
    }
    private CompilationUnit definitionUnit(IVariableBinding field) {
        if (root.findDeclaringNode(field.getVariableDeclaration().getKey()) != null) return root;
        ITypeBinding owner = field.getDeclaringClass();
        while (owner != null && owner.getDeclaringClass() != null) owner = owner.getDeclaringClass();
        if (owner == null || owner.isRecovered()) return null;
        String type = owner.getQualifiedName();
        if (units.containsKey(type)) return units.get(type).ast();
        if (units.size() >= MAX_UNITS) throw new Unavailable();
        String identity; Read read; String unitName;
        ASTParser parser = ASTParser.newParser(root.getAST().apiLevel());
        try {
            if (root.getJavaElement() != null) {
                // Only actual project source, never an unverified source attachment for a binary library.
                if (!(field.getJavaElement() instanceof IField element) || element.isBinary()) return null;
                ICompilationUnit sourceUnit = element.getCompilationUnit();
                if (sourceUnit == null || !sourceUnit.exists()) return null;
                identity = sourceUnit.getHandleIdentifier(); read = sourceUnit::getSource;
                unitName = sourceUnit.getPath().toPortableString();
                parser.setSource(sourceUnit); parser.setResolveBindings(true);
            } else {
                if (environment == null || environment.sourcepath().isEmpty()) return null;
                String relative = type.replace('.', '/') + ".java"; //$NON-NLS-1$
                final var lookupEnvironment = environment;
                SourceFile selected = locateSource(lookupEnvironment, relative);
                if (selected == null) return null;
                read = () -> {
                    if (!selected.equals(locateSource(lookupEnvironment, relative))) throw new IOException("SOURCE_RESOLUTION_CHANGED"); //$NON-NLS-1$
                    return readBounded(selected.path(), selected.encoding());
                };
                identity = selected.path().toString(); unitName = relative;
                parser.setEnvironment(environment.classpath().toArray(String[]::new), environment.sourcepath().toArray(String[]::new),
                    environment.encodings().isEmpty() ? null : environment.encodings().toArray(String[]::new), true);
                parser.setUnitName(unitName); parser.setResolveBindings(true);
            }
            String source = read.get();
            if (source == null || (long)sourceChars + source.length() > MAX_SOURCE_CHARS) throw new Unavailable();
            sourceChars += source.length(); charge(1 + source.length() / 32);
            String digest = hash(source);
            var previous = dependencies.putIfAbsent(identity, new Dependency(identity, digest, read));
            if (previous != null && !previous.sha256().equals(digest)) throw new Unavailable();
            if (root.getJavaElement() == null) {
                parser.setSource(source.toCharArray());
                var compiler = new HashMap<String,String>(); JavaCore.setComplianceOptions(targetJava == 8 ? "1.8" : Integer.toString(targetJava), compiler); //$NON-NLS-1$
                parser.setCompilerOptions(compiler);
            }
            CompilationUnit ast = (CompilationUnit)parser.createAST(monitor);
            if (!digest.equals(hash(read.get()))) throw new Unavailable();
            for (var problem : ast.getProblems()) if (problem.isError()) return null;
            Unit unit = new Unit(ast, identity, digest); units.put(type, unit);
            return ast;
        } catch (Exception unavailable) { return null; }
    }
    private record SourceFile(Path path, Charset encoding) { }
    /** Rechecked at use time, including formerly absent source roots that can introduce a competing definition. */
    private static SourceFile locateSource(MathematicalAnalysis.SourceEnvironment environment, String relative) throws IOException {
        SourceFile selected = null;
        for (int index = 0; index < environment.sourcepath().size(); index++) {
            Path rootPath = Path.of(environment.sourcepath().get(index)).toAbsolutePath().normalize();
            Path candidate = rootPath.resolve(relative).normalize();
            if (!candidate.startsWith(rootPath)) throw new IOException("SOURCE_PATH_OUTSIDE_ROOT"); //$NON-NLS-1$
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
            if (selected != null || Files.isSymbolicLink(candidate)
                    || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("AMBIGUOUS_OR_UNSAFE_SOURCE"); //$NON-NLS-1$
            Charset charset = environment.encodings().isEmpty() ? Charset.defaultCharset()
                    : Charset.forName(environment.encodings().get(index));
            selected = new SourceFile(candidate, charset);
        }
        return selected;
    }
    private static String readBounded(Path path, Charset encoding) throws IOException {
        if (Files.isSymbolicLink(path) || Files.size(path) > MAX_SOURCE_CHARS * 4L) throw new IOException("SOURCE_INPUT_BOUND"); //$NON-NLS-1$
        return Files.readString(path, encoding);
    }

    private boolean observedNonnull(IVariableBinding field, ASTNode selected, int start) {
        int ancestors = 0;
        for (ASTNode node = selected; node != null; node = node.getParent()) {
            if (++ancestors > 128) return false;
            if (node instanceof LambdaExpression || node instanceof AnonymousClassDeclaration || node instanceof MethodDeclaration) break;
            if (!(node.getParent() instanceof Block block)) continue;
            for (Object child : block.statements()) {
                charge(1);
                Statement statement = (Statement)child;
                if (statement.getStartPosition() >= node.getStartPosition() || (long)statement.getStartPosition()+statement.getLength() > start) break;
                Expression expression = statement instanceof IfStatement condition ? condition.getExpression()
                    : statement instanceof ExpressionStatement operation ? operation.getExpression() : null;
                if (nonnullUse(expression, field, 0)) return true;
            }
        }
        return false;
    }
    private boolean nonnullUse(Expression expression, IVariableBinding field, int depth) {
        charge(1);
        if (expression == null || depth > MAX_DEPTH) return false;
        expression = unwrap(expression);
        if (expression instanceof InfixExpression infix) {
            if (nonnullUse(infix.getLeftOperand(), field, depth+1)) return true;
            if (infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) return false;
            if (nonnullUse(infix.getRightOperand(), field, depth+1)) return true;
            for (Object item : infix.extendedOperands()) if (nonnullUse((Expression)item,field,depth+1)) return true;
        }
        if (expression instanceof PrefixExpression prefix) return nonnullUse(prefix.getOperand(),field,depth+1);
        if (expression instanceof MethodInvocation call && call.resolveMethodBinding() != null
                && !Modifier.isStatic(call.resolveMethodBinding().getModifiers())) {
            if (same(call.getExpression(), field)) return true;
            // On a proved exact BigInteger, compareTo cannot complete with a null argument.
            if ("compareTo".equals(call.getName().getIdentifier()) && call.arguments().size() == 1 //$NON-NLS-1$
                    && BIG_INTEGER.equals(call.resolveMethodBinding().getDeclaringClass().getQualifiedName())
                    && same((Expression)call.arguments().getFirst(),field) && exactLocal(call.getExpression(), call.getStartPosition())) return true;
        }
        return false;
    }
    private boolean exactLocal(Expression expression, int position) {
        expression = unwrap(expression);
        if (!(expression instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding binding)
                || binding.isField() || !(binding.isEffectivelyFinal() || Modifier.isFinal(binding.getModifiers()))) return false;
        if (!(root.findDeclaringNode(binding) instanceof VariableDeclarationFragment declaration) || declaration.getStartPosition() >= position) return false;
        Expression initializer = unwrap(declaration.getInitializer());
        return initializer instanceof ClassInstanceCreation creation && creation.getAnonymousClassDeclaration() == null
            && creation.resolveTypeBinding() != null && BIG_INTEGER.equals(creation.resolveTypeBinding().getQualifiedName());
    }
    private static boolean same(Expression expression, IVariableBinding binding) {
        expression = unwrap(expression);
        return expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding other
            && binding.isEqualTo(other.getVariableDeclaration());
    }
    private static Expression unwrap(Expression expression) {
        int depth = 0;
        while (expression instanceof ParenthesizedExpression parentheses && depth++ < MAX_DEPTH) expression = parentheses.getExpression();
        return expression;
    }
    private void charge(long amount) {
        work += amount;
        if (work > allowance || Thread.currentThread().isInterrupted() || monitor != null && monitor.isCanceled()) throw new Unavailable();
    }
    private static final class Unavailable extends RuntimeException { private static final long serialVersionUID = 1L; }
    private static String hash(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); } //$NON-NLS-1$
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
