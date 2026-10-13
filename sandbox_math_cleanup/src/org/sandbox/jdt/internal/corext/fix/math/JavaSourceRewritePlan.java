/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/** Immutable source-version-bound edit group. JDT objects live only in resolve(), never in the SDK graph.
 * An occurrence can reference several syntax nodes; node identity is not mathematical equality. */
public record JavaSourceRewritePlan(String unitName, String sourceSha256, int sourceLength, int start, int length,
        List<Node> nodes, List<Node> pinned, Map<String, List<String>> occurrenceOrigins, List<Change> changes) {
    private static final Pattern OCCURRENCE = Pattern.compile("operation@(\\d+):\\d+"); //$NON-NLS-1$
    public record Node(String id, int offset, int length, int nodeType, String bindingKey, String textSha256) {
        public Node {
            Objects.requireNonNull(id); Objects.requireNonNull(bindingKey); Objects.requireNonNull(textSha256);
            if (id.isBlank() || offset < 0 || length <= 0 || nodeType <= 0) throw new IllegalArgumentException("INVALID_SOURCE_NODE"); //$NON-NLS-1$
        }
    }
    public record Change(int offset, int length, String originalSha256, String replacement) {
        public Change {
            Objects.requireNonNull(originalSha256); Objects.requireNonNull(replacement);
            if (offset < 0 || length < 0) throw new IllegalArgumentException("INVALID_SOURCE_EDIT"); //$NON-NLS-1$
        }
    }
    public JavaSourceRewritePlan {
        Objects.requireNonNull(unitName); Objects.requireNonNull(sourceSha256);
        nodes = List.copyOf(nodes); pinned = List.copyOf(pinned); changes = List.copyOf(changes);
        var origins = new LinkedHashMap<String, List<String>>();
        occurrenceOrigins.forEach((id, values) -> origins.put(id, List.copyOf(values)));
        occurrenceOrigins = Map.copyOf(origins);
        if (unitName.isBlank() || start < 0 || length <= 0 || (long) start + length > sourceLength)
            throw new IllegalArgumentException("INVALID_SOURCE_PLAN"); //$NON-NLS-1$
        var ids = new HashSet<String>();
        for (var node : nodes) if (!ids.add(node.id()) || node.offset() < start || (long) node.offset() + node.length() > (long) start + length)
            throw new IllegalArgumentException("INVALID_SOURCE_ANCHOR"); //$NON-NLS-1$
        for (var origin : occurrenceOrigins.values()) if (origin.isEmpty() || !ids.containsAll(origin))
            throw new IllegalArgumentException("UNBOUND_SOURCE_OCCURRENCE"); //$NON-NLS-1$
        for (var node : pinned) if (node.offset() < start || (long) node.offset() + node.length() > (long) start + length)
            throw new IllegalArgumentException("INVALID_PINNED_ANCHOR"); //$NON-NLS-1$
        int previousEnd = start;
        for (var change : changes) {
            if (change.offset() < previousEnd || (long) change.offset() + change.length() > (long) start + length)
                throw new IllegalArgumentException("OVERLAPPING_OR_OUTSIDE_SOURCE_EDITS"); //$NON-NLS-1$
            for (var anchor : pinned) if (change.offset() < anchor.offset() + anchor.length()
                    && (change.offset() + change.length() > anchor.offset() || change.length() == 0 && change.offset() > anchor.offset()))
                throw new IllegalArgumentException("PINNED_SOURCE_EFFECT_CHANGED"); //$NON-NLS-1$
            previousEnd = change.offset() + change.length();
        }
    }

    /** Restore a request-local SourceId -> JDT node table against the exact source snapshot and bindings. */
    public Map<String, ASTNode> resolve(CompilationUnit ast, String source) {
        requireSource(source);
        var result = new LinkedHashMap<String, ASTNode>();
        var all = new ArrayList<>(nodes); all.addAll(pinned);
        for (var anchor : all) {
            ASTNode node = exact(ast, anchor.offset(), anchor.length());
            if (node.getNodeType() != anchor.nodeType() || !bindingKey(node).equals(anchor.bindingKey())
                    || !hash(source.substring(anchor.offset(), anchor.offset() + anchor.length())).equals(anchor.textSha256()))
                throw new IllegalArgumentException("STALE_SOURCE_NODE"); //$NON-NLS-1$
            result.put(anchor.id(), node);
        }
        return Map.copyOf(result);
    }

    public TextEdit newEdit(String source) {
        requireSource(source);
        for (var change : changes) if (!hash(source.substring(change.offset(), change.offset() + change.length())).equals(change.originalSha256()))
            throw new IllegalArgumentException("SOURCE_EDIT_TEXT_DIFFERS"); //$NON-NLS-1$
        return newEdit();
    }
    TextEdit newEdit() {
        var edit = new MultiTextEdit();
        for (var change : changes) edit.addChild(new ReplaceEdit(change.offset(), change.length(), change.replacement()));
        return edit;
    }
    String replacement(String source) {
        var document = new Document(source);
        try {
            newEdit(source).apply(document);
            return document.get(start, length + document.getLength() - source.length());
        } catch (BadLocationException invalid) { throw new IllegalArgumentException("SOURCE_PLAN_RANGE_INVALID", invalid); } //$NON-NLS-1$
    }
    void requireEnvelope(int offset, int size, String original, String replacement) {
        if (offset != start || size != length || original.length() != length)
            throw new IllegalArgumentException("SOURCE_PLAN_ENVELOPE_DIFFERS"); //$NON-NLS-1$
        var expected = new StringBuilder(original);
        for (var change : changes.reversed()) {
            int from = change.offset() - start, to = from + change.length();
            if (!hash(original.substring(from, to)).equals(change.originalSha256()))
                throw new IllegalArgumentException("SOURCE_EDIT_TEXT_DIFFERS"); //$NON-NLS-1$
            expected.replace(from, to, change.replacement());
        }
        if (!expected.toString().equals(replacement)) throw new IllegalArgumentException("SOURCE_PLAN_REPLACEMENT_DIFFERS"); //$NON-NLS-1$
    }

    private void requireSource(String source) {
        if (source.length() != sourceLength || !hash(source).equals(sourceSha256)) throw new IllegalArgumentException("STALE_SOURCE_PLAN"); //$NON-NLS-1$
    }

    static JavaSourceRewritePlan capture(CompilationUnit ast, String source, String unitName, JavaComputationRegion region,
            List<Statement> barriers, TextEdit edit, String prefix) {
        var nodes = new LinkedHashMap<String, Node>();
        var positions = new LinkedHashMap<Integer, List<String>>();
        var all = new ArrayList<>(region.outputs()); all.addAll(region.internalBindings());
        all.sort(Comparator.comparingInt(JavaComputationRegion.OutputBinding::statementStart));
        for (var binding : all) {
            ASTNode statement = exact(ast, binding.statementStart(), binding.statementLength());
            Node anchor = captureNode(statement, source); nodes.putIfAbsent(anchor.id(), anchor);
            ASTNode initializer = exact(ast, binding.initializerStart(), binding.initializerLength());
            initializer.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) {
                    if (node == initializer || node instanceof InfixExpression || node instanceof CastExpression || node instanceof PrefixExpression) {
                        Node origin = captureNode(node, source); nodes.putIfAbsent(origin.id(), origin);
                        positions.computeIfAbsent(node.getStartPosition(), ignored -> new ArrayList<>()).add(origin.id());
                    }
                }
            });
        }
        var origins = new LinkedHashMap<String, List<String>>();
        for (var occurrence : region.trace().occurrences()) {
            var match = OCCURRENCE.matcher(occurrence.sourceId());
            if (!match.matches()) throw new IllegalArgumentException("SOURCE_OCCURRENCE_SCHEME_UNSUPPORTED"); //$NON-NLS-1$
            var anchors = positions.get(Integer.parseInt(match.group(1)));
            if (anchors == null) throw new IllegalArgumentException("SOURCE_OCCURRENCE_UNMAPPED"); //$NON-NLS-1$
            if (origins.put(occurrence.sourceId(), anchors.stream().distinct().toList()) != null)
                throw new IllegalArgumentException("DUPLICATE_SOURCE_OCCURRENCE"); //$NON-NLS-1$
        }
        var pinned = new ArrayList<Node>(); barriers.forEach(node -> pinned.add(captureNode(node, source)));
        // Comments are not normal AST children. Pin them explicitly rather than giving a remover ownership.
        for (Object value : ast.getCommentList()) {
            ASTNode comment = (ASTNode) value;
            if (comment.getStartPosition() >= region.start() && comment.getStartPosition() + comment.getLength() <= region.start() + region.length())
                pinned.add(captureNode(comment, source));
        }
        var changes = new ArrayList<Change>(); flatten(edit, source, changes);
        changes.sort(Comparator.comparingInt(Change::offset).thenComparingInt(Change::length));
        if (!prefix.isEmpty()) {
            if (!changes.isEmpty() && changes.getFirst().offset() == region.start()) {
                var first = changes.removeFirst(); changes.addFirst(new Change(first.offset(), first.length(), first.originalSha256(), prefix + first.replacement()));
            } else changes.addFirst(new Change(region.start(), 0, hash(""), prefix)); //$NON-NLS-1$
        }
        var plan = new JavaSourceRewritePlan(unitName, hash(source), source.length(), region.start(), region.length(),
                List.copyOf(nodes.values()), pinned, origins, changes);
        plan.resolve(ast, source);
        return plan;
    }
    private static void flatten(TextEdit edit, String source, List<Change> changes) {
        if (edit.hasChildren()) { for (TextEdit child : edit.getChildren()) flatten(child, source, changes); return; }
        String replacement;
        if (edit instanceof ReplaceEdit replaced) replacement = replaced.getText();
        else if (edit instanceof InsertEdit inserted) replacement = inserted.getText();
        else if (edit instanceof DeleteEdit) replacement = ""; //$NON-NLS-1$
        else if (edit instanceof MultiTextEdit) return;
        else throw new IllegalArgumentException("UNSUPPORTED_SOURCE_EDIT_KIND"); //$NON-NLS-1$
        String original = source.substring(edit.getOffset(), edit.getExclusiveEnd());
        if (!original.equals(replacement)) changes.add(new Change(edit.getOffset(), edit.getLength(), hash(original), replacement));
    }
    private static Node captureNode(ASTNode node, String source) {
        int start = node.getStartPosition(), length = node.getLength();
        String id = "source@" + start + ":" + length + ":" + node.getNodeType(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return new Node(id, start, length, node.getNodeType(), bindingKey(node), hash(source.substring(start, start + length)));
    }
    private static String bindingKey(ASTNode node) {
        IBinding binding = null;
        if (node instanceof SimpleName name) binding = name.resolveBinding();
        else if (node instanceof VariableDeclarationStatement declaration && declaration.fragments().size() == 1)
            binding = ((VariableDeclarationFragment) declaration.fragments().getFirst()).resolveBinding();
        else if (node instanceof ExpressionStatement statement && statement.getExpression() instanceof MethodInvocation call)
            binding = call.resolveMethodBinding();
        return binding == null || binding.isRecovered() ? "" : Objects.toString(binding.getKey(), ""); //$NON-NLS-1$ //$NON-NLS-2$
    }
    private static ASTNode exact(CompilationUnit ast, int offset, int length) {
        ASTNode node = NodeFinder.perform(ast, offset, length);
        if (node != null && node.getStartPosition() == offset && node.getLength() == length) return node;
        for (Object value : ast.getCommentList()) {
            ASTNode comment = (ASTNode) value;
            if (comment.getStartPosition() == offset && comment.getLength() == length) return comment;
        }
        throw new IllegalArgumentException("STALE_SOURCE_NODE"); //$NON-NLS-1$
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } //$NON-NLS-1$
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
}
