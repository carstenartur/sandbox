/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotatableType;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Plans a newly required checked exception through handlers and source-owned
 * callable contracts. The plan retains binding keys/source identities, not ASTs.
 * No source is changed during planning. Unresolved or out-of-scope contracts are
 * reported rather than replaced by an unchecked wrapper or a String overload.
 */
public final class CheckedExceptionMigration {
    private CheckedExceptionMigration() { }

    private static final class AppliedChanges {
        final Set<String> targets= new HashSet<>();
    }

    private record UnitPlan(String source, Set<String> methods, Set<Integer> catches) {
        UnitPlan {
            methods= Set.copyOf(methods);
            catches= Set.copyOf(catches);
        }
    }

    /** Immutable local parts of one checked-exception propagation. */
    public static final class Plan {
        private final Map<String, UnitPlan> units;
        private final String addedType;
        private final String oldHandlerType;
        private final Set<String> requiredHandles;
        private final List<String> conflicts;
        private final Set<String> affected;

        private Plan(Map<String, UnitPlan> units, String addedType, String oldHandlerType,
                Set<String> requiredHandles, List<String> conflicts, Set<String> affected) {
            this.units= Map.copyOf(units);
            this.addedType= addedType;
            this.oldHandlerType= oldHandlerType;
            this.requiredHandles= Set.copyOf(requiredHandles);
            this.conflicts= List.copyOf(conflicts);
            this.affected= Set.copyOf(affected);
        }

        public Set<String> affectedCompilationUnits() { return affected; }
        public Set<String> requiredCompilationUnits() { return requiredHandles; }
        public List<String> conflicts() { return conflicts; }
        public boolean hasChanges(String handle) {
            UnitPlan unit= units.get(handle);
            return unit != null && (!unit.methods().isEmpty() || !unit.catches().isEmpty());
        }

        public void requireComplete() throws CoreException {
            if (!conflicts.isEmpty() || !requiredHandles.isEmpty()) {
                throw new CoreException(Status.error("Cannot complete checked-exception migration: " //$NON-NLS-1$
                        + String.join("; ", conflicts) //$NON-NLS-1$
                        + (requiredHandles.isEmpty() ? "" : "; additional source units required: " + requiredHandles))); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        /** Apply after expression rewrites and removal of obsolete exception handlers. */
        public void apply(CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
            requireComplete();
            CompilationUnit root= cuRewrite.getRoot();
            ICompilationUnit unit= (ICompilationUnit) root.getJavaElement();
            UnitPlan local= units.get(unit.getHandleIdentifier());
            if (local == null) {
                throw new CoreException(Status.error("Compilation unit is outside the planned source scope")); //$NON-NLS-1$
            }
            if (!local.source().equals(unit.getSource())) {
                throw new CoreException(Status.error("Source changed after checked-exception planning: " + unit.getElementName())); //$NON-NLS-1$
            }
            if (!hasChanges(unit.getHandleIdentifier())) return;
            ASTRewrite rewrite= cuRewrite.getASTRewrite();
            String property= CheckedExceptionMigration.class.getName();
            if (!(rewrite.getProperty(property) instanceof AppliedChanges)) rewrite.setProperty(property, new AppliedChanges());
            AppliedChanges applied= (AppliedChanges) rewrite.getProperty(property);
            Set<String> foundMethods= new HashSet<>();
            Set<Integer> foundCatches= new HashSet<>();
            root.accept(new ASTVisitor() {
                @Override public boolean visit(MethodDeclaration method) {
                    IMethodBinding binding= method.resolveBinding();
                    if (binding != null && local.methods().contains(key(binding))) {
                        foundMethods.add(key(binding));
                        if (!applied.targets.add(addedType + ":method:" + key(binding))) return true; //$NON-NLS-1$
                        ListRewrite list= rewrite.getListRewrite(method, MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
                        boolean covered= false;
                        boolean documented= false;
                        for (Object item : new ArrayList<>(list.getRewrittenList())) {
                            Type type= (Type) item;
                            ITypeBinding declared= type.resolveBinding();
                            if (declared == null) continue;
                            if (addedType.equals(declared.getQualifiedName())) covered= true;
                            else if (isSubtype(declared, addedType)) {
                                list.remove(type, group);
                                cuRewrite.getImportRemover().registerRemovedNode(type);
                                if (!documented) documented= ExceptionCleanupHelper.updateThrowsJavadoc(method,
                                        declared.getQualifiedName(), cuRewrite.getImportRewrite().addImport(addedType),
                                        rewrite, group, cuRewrite.getImportRemover());
                            }
                        }
                        if (!covered) {
                            list.insertLast(newType(cuRewrite), group);
                            if (!documented && method.getJavadoc() != null) {
                                TagElement tag= root.getAST().newTagElement();
                                tag.setTagName(TagElement.TAG_THROWS);
                                tag.fragments().add(root.getAST().newName(cuRewrite.getImportRewrite().addImport(addedType)));
                                TextElement description= root.getAST().newTextElement();
                                description.setText(" if the migrated operation fails"); //$NON-NLS-1$
                                tag.fragments().add(description);
                                rewrite.getListRewrite(method.getJavadoc(), Javadoc.TAGS_PROPERTY).insertLast(tag, group);
                            }
                        }
                    }
                    return true;
                }
                @Override public boolean visit(CatchClause clause) {
                    if (!local.catches().contains(clause.getStartPosition())) return true;
                    foundCatches.add(clause.getStartPosition());
                    if (!applied.targets.add(addedType + ":catch:" + clause.getStartPosition())) return true; //$NON-NLS-1$
                    if (!rewrite.getListRewrite(clause.getParent(), TryStatement.CATCH_CLAUSES_PROPERTY)
                            .getRewrittenList().contains(clause)) return true;
                    SingleVariableDeclaration parameter= clause.getException();
                    Type effective= (Type) rewrite.get(parameter, SingleVariableDeclaration.TYPE_PROPERTY);
                    List<?> alternatives= effective instanceof UnionType union
                            ? rewrite.getListRewrite(union, UnionType.TYPES_PROPERTY).getRewrittenList()
                            : List.of(effective);
                    List<Type> updated= new ArrayList<>();
                    for (Object value : alternatives) {
                        Type type= (Type) value;
                        ITypeBinding binding= type.resolveBinding();
                        if (binding != null && oldHandlerType.equals(binding.getQualifiedName())) {
                            Type replacement= newType(cuRewrite);
                            if (type instanceof AnnotatableType old && replacement instanceof AnnotatableType target)
                                target.annotations().addAll(ASTNode.copySubtrees(root.getAST(), old.annotations()));
                            updated.add(replacement);
                            cuRewrite.getImportRemover().registerRemovedNode(type);
                        } else if (isSubtype(binding, addedType)) {
                            cuRewrite.getImportRemover().registerRemovedNode(type);
                        } else updated.add((Type) ASTNode.copySubtree(root.getAST(), type));
                    }
                    Type replacement;
                    if (updated.size() == 1) replacement= updated.get(0);
                    else {
                        UnionType union= root.getAST().newUnionType();
                        union.types().addAll(updated);
                        replacement= union;
                    }
                    rewrite.set(parameter, SingleVariableDeclaration.TYPE_PROPERTY, replacement, group);
                    return true;
                }
            });
            if (!foundMethods.equals(local.methods()) || !foundCatches.equals(local.catches())) {
                throw new CoreException(Status.error("A checked-exception plan target could not be resolved")); //$NON-NLS-1$
            }
        }

        private Type newType(CompilationUnitRewrite rewrite) {
            AST ast= rewrite.getRoot().getAST();
            return ast.newSimpleType(ast.newName(rewrite.getImportRewrite().addImport(addedType)));
        }
    }

    /**
     * Plan one additional checked type for the supplied changed invocations.
     * Only the specified former handler type may be widened, and only when its
     * parameter uses remain valid without selecting different narrow overloads.
     */
    public static Plan plan(Collection<CompilationUnit> scope, Collection<? extends ASTNode> changes,
            ITypeBinding additionalException, String oldHandlerType) throws CoreException {
        return plan(scope, changes, additionalException, oldHandlerType, new NullProgressMonitor());
    }

    public static Plan plan(Collection<CompilationUnit> scope, Collection<? extends ASTNode> changes,
            ITypeBinding additionalException, String oldHandlerType, IProgressMonitor monitor) throws CoreException {
        Analysis analysis= new Analysis(scope, changes, additionalException, oldHandlerType, monitor);
        for (ASTNode changed : changes) {
            analysis.affected.add(((ICompilationUnit) ((CompilationUnit) changed.getRoot()).getJavaElement()).getHandleIdentifier());
            analysis.requireHandling(changed);
        }
        analysis.findOutsideReferences();
        return analysis.finish();
    }

    private static final class Analysis {
        private final List<CompilationUnit> roots;
        private final ITypeBinding added;
        private final String oldHandler;
        private final Set<String> addedSupertypes;
        private final Map<String, MethodDeclaration> declarations= new LinkedHashMap<>();
        private final Map<String, List<ASTNode>> references= new HashMap<>();
        private final Set<MethodDeclaration> methods= new LinkedHashSet<>();
        private final Set<CatchClause> catches= new LinkedHashSet<>();
        private final Set<String> required= new LinkedHashSet<>();
        private final Set<String> projects= new HashSet<>();
        private final List<String> conflicts= new ArrayList<>();
        private final Set<String> affected= new LinkedHashSet<>();
        private final IProgressMonitor monitor;
        private final Collection<? extends ASTNode> changedCalls;

        Analysis(Collection<CompilationUnit> scope, Collection<? extends ASTNode> changes, ITypeBinding added, String oldHandler, IProgressMonitor monitor) {
            this.changedCalls= List.copyOf(changes);
            this.monitor= monitor == null ? new NullProgressMonitor() : monitor;
            this.roots= List.copyOf(scope);
            this.added= added;
            this.oldHandler= oldHandler;
            this.addedSupertypes= supertypes(added);
            for (CompilationUnit root : roots) {
                if (root.getJavaElement() instanceof ICompilationUnit unit) projects.add(unit.getJavaProject().getHandleIdentifier());
                root.accept(new ASTVisitor() {
                    @Override public boolean visit(MethodDeclaration node) {
                        IMethodBinding method= node.resolveBinding();
                        if (method != null && !method.isRecovered()) declarations.put(key(method), node);
                        return true;
                    }
                    @Override public void preVisit(ASTNode node) {
                        IMethodBinding method= invocationBinding(node);
                        if (method != null && !method.isRecovered()) references.computeIfAbsent(key(method), k -> new ArrayList<>()).add(node);
                    }
                });
            }
        }
        private boolean covered(ITypeBinding type) {
            return type != null && !type.isRecovered() && !type.isTypeVariable()
                    && addedSupertypes.contains(type.getErasure().getQualifiedName());
        }

        void requireHandling(ASTNode invocation) {
            if (monitor.isCanceled()) throw new OperationCanceledException();
            if (invocation instanceof MethodReference reference) {
                ITypeBinding functionalType= reference.resolveTypeBinding();
                requireContract(functionalType == null ? null : functionalType.getFunctionalInterfaceMethod());
                return;
            }
            ASTNode child= invocation;
            for (ASTNode parent= invocation.getParent(); parent != null; child= parent, parent= parent.getParent()) {
                if (parent instanceof TryStatement statement
                        && (child == statement.getBody() || statement.resources().contains(child))) {
                    List<CatchClause> clauses= statement.catchClauses();
                    for (CatchClause clause : clauses) {
                        if (catches.contains(clause) || types(clause).stream().anyMatch(t -> covered(t.resolveBinding()))) return;
                    }
                    List<CatchClause> narrow= clauses.stream()
                            .filter(c -> types(c).stream().anyMatch(t -> isSubtype(t.resolveBinding(), added.getQualifiedName())))
                            .toList();
                    if (narrow.size() == 1) {
                        CatchClause clause= narrow.get(0);
                        if (types(clause).stream().anyMatch(t -> t.resolveBinding() != null
                                && oldHandler.equals(t.resolveBinding().getQualifiedName())) && canWiden(clause)
                                && CheckedExceptionAnalysis.canWidenCatch(statement, clause, changedCalls, added)) {
                            catches.add(clause);
                            // Precise rethrow must be propagated beyond, not back into, this try.
                            clause.getBody().accept(new ASTVisitor() {
                                @Override public boolean visit(ThrowStatement node) {
                                    Expression expression= node.getExpression();
                                    while (expression instanceof ParenthesizedExpression nested) expression= nested.getExpression();
                                    if (expression instanceof Name name && sameVariable(name.resolveBinding(), clause.getException().resolveBinding()))
                                        requireHandling(node);
                                    return true;
                                }
                            });
                            return;
                        }
                    }
                    if (narrow.stream().anyMatch(c -> types(c).stream().anyMatch(t -> t.resolveBinding() != null
                            && oldHandler.equals(t.resolveBinding().getQualifiedName())))) {
                        conflicts.add("Cannot safely widen the existing " + oldHandler + " handler to " + added.getQualifiedName()); //$NON-NLS-1$ //$NON-NLS-2$
                        return;
                    }
                }
                if (parent instanceof LambdaExpression lambda) {
                    requireContract(lambda.resolveMethodBinding());
                    return;
                }
                if (parent instanceof MethodDeclaration method) {
                    requireContract(method.resolveBinding());
                    return;
                }
                if (parent instanceof Initializer || parent instanceof FieldDeclaration) {
                    // A caught exception returns above. An uncaught instance initializer
                    // belongs to every constructor, never to an enclosing method.
                    if (Modifier.isStatic(((BodyDeclaration) parent).getModifiers())) {
                        conflicts.add("A static initializer has no checked-exception contract"); //$NON-NLS-1$
                        return;
                    }
                    ASTNode owner= parent.getParent();
                    ITypeBinding type= owner instanceof AbstractTypeDeclaration declaration ? declaration.resolveBinding()
                            : owner instanceof AnonymousClassDeclaration anonymous ? anonymous.resolveBinding() : null;
                    if (type == null || type.isAnonymous()) {
                        conflicts.add("Cannot update an implicit/anonymous constructor contract"); //$NON-NLS-1$
                        return;
                    }
                    for (IMethodBinding method : type.getDeclaredMethods()) if (method.isConstructor()) requireContract(method);
                    return;
                }
            }
            conflicts.add("No callable contract for changed invocation at " + invocation.getStartPosition()); //$NON-NLS-1$
        }
        private void requireContract(IMethodBinding binding) {
            if (binding == null || binding.isRecovered()) {
                conflicts.add("Unresolved callable contract"); //$NON-NLS-1$
                return;
            }
            for (ITypeBinding exception : binding.getExceptionTypes()) if (covered(exception)) return;
            MethodDeclaration declaration= declarations.get(key(binding));
            if (declaration == null) {
                IJavaElement element= binding.getMethodDeclaration().getJavaElement();
                ICompilationUnit unit= element == null ? null : (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
                if (unit != null && projects.contains(unit.getJavaProject().getHandleIdentifier())) required.add(unit.getHandleIdentifier());
                else if (unit != null) conflicts.add("Callable contract outside selected Java project scope: " + unit.getHandleIdentifier()); //$NON-NLS-1$
                else conflicts.add("Fixed external contract: " + binding.getDeclaringClass().getQualifiedName() + '.' + binding.getName()); //$NON-NLS-1$
                return;
            }
            if (!methods.add(declaration)) return;
            // All inherited contracts constrain an override, not just the first one.
            Set<String> seen= new HashSet<>();
            List<ITypeBinding> hierarchy= new ArrayList<>();
            ITypeBinding owner= binding.getDeclaringClass();
            if (owner.getSuperclass() != null) hierarchy.add(owner.getSuperclass());
            hierarchy.addAll(List.of(owner.getInterfaces()));
            for (int i= 0; i < hierarchy.size(); i++) {
                ITypeBinding type= hierarchy.get(i);
                if (!seen.add(type.getKey())) continue;
                for (IMethodBinding inherited : type.getDeclaredMethods()) if (binding.overrides(inherited)) requireContract(inherited);
                if (type.getSuperclass() != null) hierarchy.add(type.getSuperclass());
                hierarchy.addAll(List.of(type.getInterfaces()));
            }
            for (ASTNode reference : references.getOrDefault(key(binding), List.of())) requireHandling(reference);
        }
        private boolean canWiden(CatchClause clause) {
            IVariableBinding variable= clause.getException().resolveBinding();
            if (variable == null) return false;
            boolean[] safe= { true };
            clause.getBody().accept(new ASTVisitor() {
                @Override public boolean visit(SimpleName node) {
                    if (!sameVariable(node.resolveBinding(), variable)) return true;
                    ASTNode value= node;
                    while (value.getParent() instanceof ParenthesizedExpression) value= value.getParent();
                    ASTNode parent= value.getParent();
                    boolean accepted= false;
                    if (parent instanceof MethodInvocation call) {
                        IMethodBinding method= call.resolveMethodBinding();
                        if (method != null && method.getTypeArguments().length == 0) {
                            if (call.getExpression() == value) accepted= addedSupertypes.contains(method.getDeclaringClass().getErasure().getQualifiedName())
                                    && !"getClass".equals(method.getName()); //$NON-NLS-1$
                            else accepted= acceptsArgument(method, call.arguments().indexOf(value));
                        }
                    } else if (parent instanceof ClassInstanceCreation call) {
                        accepted= acceptsArgument(call.resolveConstructorBinding(), call.arguments().indexOf(value));
                    } else if (parent instanceof ThrowStatement) accepted= true;
                    else if (parent instanceof ReturnStatement) {
                        ASTNode enclosing= parent.getParent();
                        while (enclosing != null && !(enclosing instanceof MethodDeclaration) && !(enclosing instanceof LambdaExpression)) enclosing= enclosing.getParent();
                        IMethodBinding method= enclosing instanceof MethodDeclaration m ? m.resolveBinding()
                                : enclosing instanceof LambdaExpression l ? l.resolveMethodBinding() : null;
                        accepted= method != null && covered(method.getReturnType());
                    } else if (parent instanceof Assignment assignment && assignment.getRightHandSide() == value) {
                        accepted= covered(assignment.getLeftHandSide().resolveTypeBinding());
                    } else if (parent instanceof VariableDeclarationFragment fragment && fragment.getInitializer() == value) {
                        IVariableBinding target= fragment.resolveBinding();
                        ASTNode declaration= fragment.getParent();
                        boolean inferred= declaration instanceof VariableDeclarationStatement statement && statement.getType().isVar();
                        accepted= !inferred && target != null && covered(target.getType());
                    } else if (parent instanceof InstanceofExpression || parent instanceof InfixExpression) {
                        accepted= true;
                    }
                    if (!accepted) safe[0]= false;
                    return true;
                }
            });
            return safe[0];
        }
        private boolean acceptsArgument(IMethodBinding method, int index) {
            if (method == null || method.isRecovered() || index < 0 || method.getTypeArguments().length != 0) return false;
            ITypeBinding[] parameters= method.getParameterTypes();
            if (index >= parameters.length) return false;
            // Do not infer a new varargs/overload selection while changing a handler.
            return !method.isVarargs() && covered(parameters[index]);
        }
        void findOutsideReferences() throws CoreException {
            Set<String> selected= new HashSet<>();
            // Search workspace-wide for safety; only projects represented by the planned roots may expand.
            List<ICompilationUnit> working= new ArrayList<>();
            for (CompilationUnit root : roots) if (root.getJavaElement() instanceof ICompilationUnit unit) {
                selected.add(unit.getHandleIdentifier()); working.add(unit);
            }
            SearchEngine engine= new SearchEngine(working.toArray(ICompilationUnit[]::new));
            for (MethodDeclaration declaration : methods) {
                IMethodBinding binding= declaration.resolveBinding();
                if (!(binding.getJavaElement() instanceof IMethod method)) {
                    conflicts.add("Source method has no Java model handle: " + binding.getName()); //$NON-NLS-1$
                    continue;
                }
                SearchPattern pattern= SearchPattern.createPattern(method, IJavaSearchConstants.REFERENCES,
                        SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE | SearchPattern.R_ERASURE_MATCH);
                if (pattern == null) { conflicts.add("Cannot search callers of " + method.getElementName()); continue; } //$NON-NLS-1$
                engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                        SearchEngine.createWorkspaceScope(), new SearchRequestor() {
                            @Override public void acceptSearchMatch(SearchMatch match) {
                                if (match.isInsideDocComment()) return;
                                if (match.getAccuracy() != SearchMatch.A_ACCURATE) {
                                    conflicts.add("Unresolved caller of " + method.getElementName()); return; //$NON-NLS-1$
                                }
                                IJavaElement element= match.getElement() instanceof IJavaElement e ? e : null;
                                ICompilationUnit unit= element == null ? null : (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
                                if (unit != null && !selected.contains(unit.getHandleIdentifier())) {
                                    if (projects.contains(unit.getJavaProject().getHandleIdentifier())) required.add(unit.getHandleIdentifier());
                                    else conflicts.add("Caller outside selected Java project scope: " + unit.getHandleIdentifier()); //$NON-NLS-1$
                                }
                            }
                        }, monitor);
            }
        }
        Plan finish() throws CoreException {
            Map<String, UnitPlan> result= new LinkedHashMap<>();
            for (CompilationUnit root : roots) {
                if (!(root.getJavaElement() instanceof ICompilationUnit unit)) continue;
                Set<String> methodKeys= new LinkedHashSet<>();
                Set<Integer> catchOffsets= new LinkedHashSet<>();
                for (MethodDeclaration method : methods) if (method.getRoot() == root) methodKeys.add(key(method.resolveBinding()));
                for (CatchClause clause : catches) if (clause.getRoot() == root) catchOffsets.add(clause.getStartPosition());
                if (!methodKeys.isEmpty() || !catchOffsets.isEmpty()) affected.add(unit.getHandleIdentifier());
                result.put(unit.getHandleIdentifier(), new UnitPlan(unit.getSource(), methodKeys, catchOffsets));
            }
            return new Plan(result, added.getQualifiedName(), oldHandler, required, conflicts, affected);
        }
    }
    private static boolean sameVariable(IBinding first, IVariableBinding second) {
        return first instanceof IVariableBinding variable && second != null
                && variable.getVariableDeclaration().isEqualTo(second.getVariableDeclaration());
    }
    private static String key(IMethodBinding method) { return method.getMethodDeclaration().getKey(); }
    private static List<Type> types(CatchClause clause) {
        Type type= clause.getException().getType();
        return type instanceof UnionType union ? union.types() : List.of(type);
    }
    private static boolean isSubtype(ITypeBinding type, String parent) {
        return type != null && !type.isRecovered() && !type.isTypeVariable() && supertypes(type).contains(parent);
    }
    private static Set<String> supertypes(ITypeBinding type) {
        Set<String> names= new HashSet<>();
        List<ITypeBinding> pending= new ArrayList<>();
        pending.add(type);
        for (int i= 0; i < pending.size(); i++) {
            ITypeBinding next= pending.get(i);
            if (next == null || next.isRecovered() || !names.add(next.getErasure().getQualifiedName())) continue;
            if (next.getSuperclass() != null) pending.add(next.getSuperclass());
            pending.addAll(List.of(next.getInterfaces()));
        }
        return names;
    }
    private static IMethodBinding invocationBinding(ASTNode node) {
        if (node instanceof MethodInvocation call) return call.resolveMethodBinding();
        if (node instanceof SuperMethodInvocation call) return call.resolveMethodBinding();
        if (node instanceof ClassInstanceCreation call) return call.resolveConstructorBinding();
        if (node instanceof ConstructorInvocation call) return call.resolveConstructorBinding();
        if (node instanceof SuperConstructorInvocation call) return call.resolveConstructorBinding();
        if (node instanceof MethodReference reference) return reference.resolveMethodBinding();
        return null;
    }
}
