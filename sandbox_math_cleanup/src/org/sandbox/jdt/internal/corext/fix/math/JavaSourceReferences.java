/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Thread-confined, closeable source-origin lookup for one validated source snapshot.
 * Handles contain no JDT node or enclosing lookup reference. Never attach this
 * lookup to a semantic Expr, search cache or persistent proof. Resolve against a
 * freshly parsed, unmodified AST; edits must still use the source plan's validation.
 */
public final class JavaSourceReferences implements AutoCloseable {
    /** Opaque typed handle, usable only with the lookup which issued it. */
    public static final class Reference<T extends ASTNode> {
        private final Object scope;
        private final String id;
        private final Class<T> type;
        private Reference(Object scope, String id, Class<T> type) {
            this.scope = scope; this.id = id; this.type = type;
        }
        public String sourceId() { return id; }
        public Class<T> nodeType() { return type; }
    }
    private final Object scope = new Object();
    private final Map<String, ASTNode> nodes;
    private Map<String, List<String>> origins;
    private AST ast;
    private final long modificationCount;

    private JavaSourceReferences(JavaSourceRewritePlan plan, CompilationUnit unit, String source) {
        nodes = new HashMap<>(plan.resolve(unit, source));
        origins = plan.occurrenceOrigins();
        ast = unit.getAST();
        modificationCount = ast.modificationCount();
    }
    public static JavaSourceReferences open(JavaSourceRewritePlan plan, CompilationUnit unit, String source) {
        return new JavaSourceReferences(Objects.requireNonNull(plan), Objects.requireNonNull(unit),
                Objects.requireNonNull(source));
    }
    public <T extends ASTNode> Reference<T> reference(String id, Class<T> type) {
        requireOpen();
        Objects.requireNonNull(id); Objects.requireNonNull(type);
        ASTNode node = nodes.get(id);
        if (node == null) throw new IllegalArgumentException("UNKNOWN_SOURCE_REFERENCE"); //$NON-NLS-1$
        if (!type.isInstance(node)) throw new IllegalArgumentException("SOURCE_REFERENCE_TYPE_DIFFERS"); //$NON-NLS-1$
        return new Reference<>(scope, id, type);
    }
    public <T extends ASTNode> T get(Reference<T> reference) {
        requireOpen();
        Objects.requireNonNull(reference);
        if (reference.scope != scope) throw new IllegalArgumentException("FOREIGN_SOURCE_REFERENCE"); //$NON-NLS-1$
        return reference.type.cast(Objects.requireNonNull(nodes.get(reference.id)));
    }
    /** A shared semantic occurrence may have several independent source origins. */
    public List<Reference<ASTNode>> origins(String occurrenceId) {
        requireOpen();
        List<String> ids = origins.get(Objects.requireNonNull(occurrenceId));
        if (ids == null) throw new IllegalArgumentException("UNKNOWN_SOURCE_OCCURRENCE"); //$NON-NLS-1$
        return ids.stream().map(id -> reference(id, ASTNode.class)).toList();
    }
    /** Diagnostic count, also usable after close to confirm this table retained no nodes. */
    public int size() { return nodes.size(); }
    private void requireOpen() {
        AST current = ast;
        if (current == null) throw new IllegalStateException("SOURCE_REFERENCES_CLOSED"); //$NON-NLS-1$
        if (current.modificationCount() != modificationCount)
            throw new IllegalStateException("SOURCE_AST_MODIFIED"); //$NON-NLS-1$
    }
    /** Releases this table's references. Previously returned AST nodes remain owned by their callers. */
    @Override public void close() {
        nodes.clear();
        origins = Map.of();
        ast = null;
    }
}
