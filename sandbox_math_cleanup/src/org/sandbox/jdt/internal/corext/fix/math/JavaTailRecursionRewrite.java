/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.model.StateIteration;

/**
 * Bounded tail-loop planning for standalone sources and resolved native project ASTs.
 * The native bridge requires separate whole-file frame-elision consent; ordinary
 * mathematics analysis and save actions never invoke it implicitly.
 * Numerical Java behavior is retained; frame/stack resource behavior requires
 * explicit opt-in. A plan owns its source snapshots, not a mutable AST.
 */
public final class JavaTailRecursionRewrite {
    private static final int MAX_SOURCE = 1_000_000;
    public enum StackPolicy { PRESERVE_STACK, ALLOW_FRAME_ELISION }
    public record Result(Optional<Plan> plan, String reason, long work) { }

    public static final class Plan {
        private final String before;
        private final String after;
        private final int bodyOffset,bodyLength;
        private Plan(String before, String after, int bodyOffset, int bodyLength) {
            this.before = before; this.after = after; this.bodyOffset=bodyOffset; this.bodyLength=bodyLength;
        }
        public String preview() { return after; }
        public int bodyOffset() { return bodyOffset; }
        public int bodyLength() { return bodyLength; }
        public String applyTo(String current) {
            if (!before.equals(current)) throw new IllegalStateException("STALE_RECURSION_SOURCE"); //$NON-NLS-1$
            return after;
        }
        public String undo(String current) {
            if (!after.equals(current)) throw new IllegalStateException("STALE_RECURSION_RESULT"); //$NON-NLS-1$
            return before;
        }
    }

    private JavaTailRecursionRewrite() { }

    static Result prepare(String unitName, String source, int methodOffset, StackPolicy policy,
            long maximum, BooleanSupplier cancelled) {
        Objects.requireNonNull(unitName); Objects.requireNonNull(source);
        Objects.requireNonNull(policy); Objects.requireNonNull(cancelled);
        if (maximum < 0) throw new IllegalArgumentException("Negative rewrite budget"); //$NON-NLS-1$
        var work = new Work(maximum, cancelled);
        try {
            work.tick();
            if (policy != StackPolicy.ALLOW_FRAME_ELISION) throw new Stopped("STACK_CHANGE_NOT_ACCEPTED"); //$NON-NLS-1$
            if (source.length() > MAX_SOURCE || methodOffset < 0 || methodOffset >= source.length()
                    || unitName.isEmpty() || unitName.length() > 4096) throw new Stopped("SOURCE_LIMIT_OR_POSITION"); //$NON-NLS-1$
            CompilationUnit original = parse(unitName, source, work);
            return prepared(original,source,methodOffset,work,null,(text,edits)->parse(unitName,text,work));
        } catch (Stopped stopped) {
            return new Result(Optional.empty(), stopped.getMessage(), work.used);
        } catch (OperationCanceledException cancelledParse) {
            return new Result(Optional.empty(), "CANCELLED", work.used); //$NON-NLS-1$
        }
    }

    /** Native bridge: the existing project AST must match the complete current source. */
    public static Result prepare(CompilationUnit original,String source,int methodOffset,
            MathCleanUpOptions options,IProgressMonitor progress) {
        Objects.requireNonNull(original);Objects.requireNonNull(source);Objects.requireNonNull(options);
        IProgressMonitor monitor=progress==null?new NullProgressMonitor():progress;
        var token=MathematicalAnalysis.cancellation(monitor,options);
        var work=new Work(options.workBudget(),token::isCancelled);
        try {
            work.tick();
            if(!options.enabled() || !options.tailRecursion()) throw new Stopped("STACK_CHANGE_NOT_ACCEPTED"); //$NON-NLS-1$
            if(source.length()>MAX_SOURCE || methodOffset<0 || methodOffset>=source.length()) throw new Stopped("SOURCE_LIMIT_OR_POSITION"); //$NON-NLS-1$
            Map<String,String> compiler=original.getJavaElement()==null?Map.of():original.getJavaElement().getJavaProject().getOptions(true);
            if(!MathematicalAnalysis.matchesAstSource(original,source,compiler,options.targetJava(),work::charge,monitor))
                throw new Stopped("STALE_RECURSION_SOURCE"); //$NON-NLS-1$
            return prepared(original,source,methodOffset,work,options,(text,edits)->{
                work.charge(4L*text.length());
                return MathematicalAnalysis.checkGeneratedSource(original,source,text,compiler,options.targetJava(),monitor,null,edits);
            });
        } catch(Stopped stopped) {
            return new Result(Optional.empty(),stopped.getMessage(),work.used);
        } catch(OperationCanceledException cancelled) {
            return new Result(Optional.empty(),"CANCELLED",work.used); //$NON-NLS-1$
        } catch(IllegalArgumentException invalid) {
            return new Result(Optional.empty(),invalid.getMessage(),work.used);
        }
    }

    private static Result prepared(CompilationUnit original,String source,int methodOffset,Work work,
            MathCleanUpOptions options,BiFunction<String,List<GeneratedSourceDiagnostics.Edit>,CompilationUnit> parser) {
        MethodDeclaration method=methodAt(original,methodOffset,work);
        if(method==null) throw new Stopped("METHOD_NOT_FOUND"); //$NON-NLS-1$
        var inspected=JavaTailRecursionInspector.inspect(method,source,work.maximum-work.used,work.cancelled);
        work.charge(inspected.work());
        StateIteration model=inspected.iteration().orElseThrow(()->new Stopped(inspected.reason()));
        if(options!=null) {
            var kinds=new ArrayList<>(model.states().stream().map(StateIteration.State::type).toList());
            kinds.add(method.resolveBinding().getReturnType().getName());
            for(String kind:kinds) if(!options.kinds().contains(de.regelsuche.sdk.optimization.NumericKind.valueOf(kind.toUpperCase(java.util.Locale.ROOT))))
                throw new Stopped("TAIL_NUMERIC_KIND_NOT_SELECTED"); //$NON-NLS-1$
        }
        boolean counted=options!=null && options.safety()==de.regelsuche.sdk.optimization.SafetyProfile.PRESERVE_JAVA
                && !options.mathematicalOptIn() && options.goal()==de.regelsuche.sdk.optimization.OptimizationGoal.LOWER_ESTIMATED_RUNTIME;
        String body=emit(method,model,source,work,counted);
        int start=method.getBody().getStartPosition(),end=start+method.getBody().getLength();
        var after=new StringBuilder(source).replace(start,end,body);
        var diagnosticEdits=new ArrayList<GeneratedSourceDiagnostics.Edit>();
        diagnosticEdits.add(new GeneratedSourceDiagnostics.Edit(start,end-start,body.length()));
        if(options!=null) {
            var documentation=new MathematicalJavadoc();
            documentation.add(original,start,end-start,"Tail recursion: explicit ALLOW_FRAME_ELISION removes recursive frames and may change stack observation or StackOverflowError. Primitive state and evaluation order were independently replayed."); //$NON-NLS-1$
            for(var edit:documentation.edits(source)) {
                diagnosticEdits.add(new GeneratedSourceDiagnostics.Edit(edit.offset(),edit.length(),edit.replacement().length()));
                after.replace(edit.offset(),edit.offset()+edit.length(),edit.replacement());
            }
        }
        if(after.length()>MAX_SOURCE) throw new Stopped("GENERATED_SOURCE_LIMIT"); //$NON-NLS-1$
        work.charge(1L+after.length()/64L);String generated=after.toString();
        CompilationUnit emitted=parser.apply(generated,diagnosticEdits);
        MethodDeclaration replacement=methodAt(emitted,methodOffset,work);
        if(replacement==null || !JavaTailRecursionReplay.verify(method,replacement,model,work::tick)) throw new Stopped("STATE_TRANSFER_REPLAY_FAILED"); //$NON-NLS-1$
        work.tick();
        return new Result(Optional.of(new Plan(source,generated,replacement.getBody().getStartPosition(),replacement.getBody().getLength())),"VERIFIED_WITH_FRAME_ELISION",work.used); //$NON-NLS-1$
    }

    private static CompilationUnit parse(String unitName, String source, Work work) {
        work.charge(1L + source.length() / 64L);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        var options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
        parser.setCompilerOptions(options);
        parser.setUnitName(unitName); parser.setSource(source.toCharArray());
        parser.setEnvironment(null, null, null, true);
        parser.setResolveBindings(true); parser.setBindingsRecovery(false);
        var result = (CompilationUnit) parser.createAST(new NullProgressMonitor() {
            @Override public boolean isCanceled() {
                return work.cancelled.getAsBoolean() || Thread.currentThread().isInterrupted();
            }
        });
        work.tick();
        for (var problem : result.getProblems()) {
            work.tick();
            if (problem.isError()) throw new Stopped("COMPILATION_ERRORS"); //$NON-NLS-1$
        }
        return result;
    }

    private static MethodDeclaration methodAt(CompilationUnit unit, int offset, Work work) {
        var result = new ArrayList<MethodDeclaration>();
        unit.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.tick(); }
            @Override public boolean visit(MethodDeclaration method) {
                if (method.getStartPosition() == offset) {
                    result.add(method);
                    return false;
                }
                return true;
            }
        });
        return result.size() == 1 ? result.getFirst() : null;
    }

    private static String emit(MethodDeclaration method, StateIteration model, String source, Work work, boolean counted) {
        var reserved = new HashSet<String>();
        model.states().forEach(s -> reserved.add(s.name()));
        var states = new ArrayList<String>(); var next = new ArrayList<String>();
        for (int i = 0; i < model.states().size(); i++) {
            states.add(fresh("_state", reserved)); next.add(fresh("_next", reserved)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        var names = new HashMap<String, String>();
        for (int i = 0; i < states.size(); i++) names.put(model.states().get(i).bindingKey(), states.get(i));
        var exit = (IfStatement) method.getBody().statements().getFirst();
        var tail = (ReturnStatement) method.getBody().statements().get(1);
        var call = (MethodInvocation) JavaPrimitiveStateExpressions.unwrap(tail.getExpression());
        ReturnStatement base = exit.getThenStatement() instanceof ReturnStatement r ? r
                : (ReturnStatement) ((Block) exit.getThenStatement()).statements().getFirst();
        var code = new StringBuilder("{\n"); //$NON-NLS-1$
        for (int i = 0; i < states.size(); i++) {
            var state = model.states().get(i);
            code.append("    ").append(state.type()).append(' ').append(states.get(i)).append(" = ").append(state.name()).append(";\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        int counter=-1;
        if(counted) for(int i=0;i<states.size();i++) if(model.states().get(i).bindingKey().equals(model.termination().counterBinding())) counter=i;
        if(counter<0) code.append("    while (!( ").append(renamed(exit.getExpression(), names, source, work)).append(" )) {\n"); //$NON-NLS-1$ //$NON-NLS-2$
        else {
            String name=states.get(counter);
            code.append("    for (; ").append(name).append(" > 0; ").append(name).append("--) {\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        for (int i = 0; i < next.size(); i++) {
            if(i==counter) continue;
            code.append("        final ").append(model.states().get(i).type()).append(' ').append(next.get(i)).append(" = ") //$NON-NLS-1$ //$NON-NLS-2$
                .append(renamed((Expression) call.arguments().get(i), names, source, work)).append(";\n"); //$NON-NLS-1$
        }
        for (int i = 0; i < next.size(); i++) {
            if(i==counter) continue;
            code.append("        ").append(states.get(i)).append(" = ").append(next.get(i)).append(";\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        code.append("    }\n    return ").append(renamed(base.getExpression(), names, source, work)).append(";\n}"); //$NON-NLS-1$ //$NON-NLS-2$
        work.charge(1L + code.length() / 64L);
        return code.toString();
    }

    private static String fresh(String prefix, HashSet<String> reserved) {
        for (int i = 0; ; i++) if (reserved.add(prefix + i)) return prefix + i;
    }

    private record Replacement(int start, int length, String text) { }
    private static String renamed(Expression expression, Map<String, String> names, String source, Work work) {
        int start = expression.getStartPosition();
        work.charge(1L + expression.getLength() / 64L);
        var edits = new ArrayList<Replacement>();
        expression.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.tick(); }
            @Override public boolean visit(SimpleName name) {
                if (name.resolveBinding() instanceof IVariableBinding binding) {
                    String target = names.get(binding.getVariableDeclaration().getKey());
                    if (target == null) throw new Stopped("UNBOUND_STATE_READ"); //$NON-NLS-1$
                    edits.add(new Replacement(name.getStartPosition() - start, name.getLength(), target));
                }
                return false;
            }
        });
        var text = new StringBuilder(source.substring(start, start + expression.getLength()));
        // AST leaves are visited in source order; edit backwards to preserve offsets.
        for (int i = edits.size() - 1; i >= 0; i--) {
            work.tick(); var edit = edits.get(i);
            text.replace(edit.start(), edit.start() + edit.length(), edit.text());
        }
        return text.toString();
    }

    private static final class Work {
        private final long maximum;
        private final BooleanSupplier cancelled;
        private long used;
        Work(long maximum, BooleanSupplier cancelled) { this.maximum = maximum; this.cancelled = cancelled; }
        void tick() { charge(1); }
        void charge(long amount) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Stopped("CANCELLED"); //$NON-NLS-1$
            if (amount < 0 || amount > maximum - used) throw new Stopped("WORK_LIMIT"); //$NON-NLS-1$
            used += amount;
        }
    }
    private static final class Stopped extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Stopped(String reason) { super(reason, null, false, false); }
    }
}
