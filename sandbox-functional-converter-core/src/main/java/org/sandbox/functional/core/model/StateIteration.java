/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sandbox.functional.core.model.UlrExpression.Type;

/**
 * An immutable multi-state iteration view, independent of stream rendering.
 * Producers retain binding identities and distinguish evaluation order from
 * assignment order. Updates are in evaluation order. For simultaneous updates,
 * every expression sees the old state. Expression types are the types at evaluation;
 * the state and result types describe the receiving boundaries.
 * A consumer must discharge the obligations before any source transformation.
 */
public record StateIteration(List<State> states, UlrExpression stopCondition, List<Update> updates,
        UlrExpression resultExpression, Type resultType, UpdateMode updateMode, EvaluationOrder evaluationOrder,
        CountDown termination, Set<Obligation> obligations) {
    public static final int MAX_STATES = 8;
    private static final int MAX_BINDING_LENGTH = 4_096;
    private static final int MAX_EXPRESSION_NODES = 65_536;

    public enum UpdateMode { SIMULTANEOUS, SEQUENTIAL }
    public enum EvaluationOrder { LEFT_TO_RIGHT }
    public enum Obligation { SOURCE_REVALIDATION, JAVA_EVALUATION_REPLAY, STACK_BEHAVIOR_CHANGE }

    public record State(String bindingKey, String name, Type type) {
        public State {
            text(bindingKey, MAX_BINDING_LENGTH);
            text(name, MAX_BINDING_LENGTH);
            Objects.requireNonNull(type);
        }
    }

    public record Update(String targetBinding, UlrExpression expression) {
        public Update {
            text(targetBinding, MAX_BINDING_LENGTH);
            Objects.requireNonNull(expression);
        }
    }

    /** A producer's countdown description, not a proof of termination or range safety. */
    public record CountDown(String counterBinding, long decrement, long stopAtOrBelow) {
        public CountDown {
            text(counterBinding, MAX_BINDING_LENGTH);
            if (decrement <= 0) throw new IllegalArgumentException("A countdown must decrease");
        }
    }

    public StateIteration {
        states = snapshot(states, MAX_STATES);
        updates = snapshot(updates, MAX_STATES);
        obligations = Set.copyOf(snapshot(obligations, Obligation.values().length));
        Objects.requireNonNull(stopCondition);
        Objects.requireNonNull(resultExpression);
        Objects.requireNonNull(resultType);
        Objects.requireNonNull(updateMode);
        Objects.requireNonNull(evaluationOrder);
        Objects.requireNonNull(termination);
        if (states.isEmpty() || states.size() != updates.size()) {
            throw new IllegalArgumentException("Every state needs exactly one update");
        }
        if (stopCondition.type() != Type.BOOLEAN || !assignable(resultExpression.type(), resultType)) {
            throw new IllegalArgumentException("Invalid iteration condition or result type");
        }
        var bindings = new HashMap<String, Type>();
        var names = new HashSet<String>();
        for (State state : states) {
            if (bindings.putIfAbsent(state.bindingKey(), state.type()) != null || !names.add(state.name())) {
                throw new IllegalArgumentException("State identities and names must be distinct");
            }
        }
        Type counter = bindings.get(termination.counterBinding());
        if (counter == null || counter == Type.BOOLEAN) {
            throw new IllegalArgumentException("The counter must be a declared numeric state");
        }
        var targets = new HashSet<String>();
        for (Update update : updates) {
            Type target = bindings.get(update.targetBinding());
            if (target == null || !targets.add(update.targetBinding()) || !assignable(update.expression().type(), target)) {
                throw new IllegalArgumentException("Unknown, duplicate or incompatible update target");
            }
        }
        validateReads(bindings, stopCondition, resultExpression, updates);
    }

    /** Make the immutable boundary explicit to callers and bytecode analyzers. */
    @Override public List<State> states() { return List.copyOf(states); }
    @Override public List<Update> updates() { return List.copyOf(updates); }

    private static boolean assignable(Type from, Type to) {
        return from == to || from == Type.INT32 && to == Type.INT64;
    }

    private static void validateReads(Map<String, Type> bindings, UlrExpression stop, UlrExpression result,
            List<Update> updates) {
        var pending = new ArrayDeque<UlrExpression>();
        pending.push(stop);
        pending.push(result);
        for (Update update : updates) pending.push(update.expression());
        // Long left-associated chains are valid, and producers may share immutable subtrees.
        var seen = Collections.newSetFromMap(new IdentityHashMap<UlrExpression, Boolean>());
        while (!pending.isEmpty()) {
            UlrExpression node = pending.pop();
            if (!seen.add(node)) continue;
            if (seen.size() > MAX_EXPRESSION_NODES) throw new IllegalArgumentException("State-iteration expression limit");
            switch (node) {
                case UlrExpression.Read read -> {
                    if (bindings.get(read.binding()) != read.type()) {
                        throw new IllegalArgumentException("Unbound or incorrectly typed state read");
                    }
                }
                case UlrExpression.Unary unary -> pending.push(unary.operand());
                case UlrExpression.Binary binary -> {
                    pending.push(binary.right());
                    pending.push(binary.left());
                }
                case UlrExpression.Constant ignored -> { }
            }
        }
    }

    private static void text(String value, int maximum) {
        if (value == null || value.length() > maximum || value.isBlank()) {
            throw new IllegalArgumentException("Missing or oversized state-iteration text");
        }
    }

    private static <T> List<T> snapshot(Collection<T> source, int maximum) {
        Objects.requireNonNull(source);
        var copy = new ArrayList<T>();
        // Bound actual iteration as well as advertised size before copying.
        if (source.size() > maximum) throw new IllegalArgumentException("State-iteration limit");
        for (T value : source) {
            if (copy.size() == maximum) throw new IllegalArgumentException("State-iteration limit");
            copy.add(Objects.requireNonNull(value));
        }
        return List.copyOf(copy);
    }
}
