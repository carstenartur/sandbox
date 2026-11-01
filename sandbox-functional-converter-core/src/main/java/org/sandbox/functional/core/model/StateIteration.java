/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable multi-state iteration view, independent of stream rendering.
 * Producers retain binding identities and distinguish evaluation order from
 * assignment order. For simultaneous updates every expression sees the old state.
 * Source fragments are inspection data, not executable or verified replacements.
 * A consumer must discharge the obligations before any source transformation.
 */
public record StateIteration(List<State> states, String stopCondition, List<Update> updates,
        String resultExpression, UpdateMode updateMode, EvaluationOrder evaluationOrder,
        CountDown termination, Set<Obligation> obligations) {
    public static final int MAX_STATES = 8;
    public static final int MAX_FRAGMENT_LENGTH = 65_536;
    private static final int MAX_BINDING_LENGTH = 4_096;

    public enum UpdateMode { SIMULTANEOUS, SEQUENTIAL }
    public enum EvaluationOrder { LEFT_TO_RIGHT }
    public enum Obligation { SOURCE_REVALIDATION, JAVA_EVALUATION_REPLAY, STACK_BEHAVIOR_CHANGE }

    public record State(String bindingKey, String name, String type) {
        public State {
            text(bindingKey, MAX_BINDING_LENGTH);
            text(name, MAX_BINDING_LENGTH);
            text(type, MAX_BINDING_LENGTH);
        }
    }

    public record Update(String targetBinding, String expression) {
        public Update {
            text(targetBinding, MAX_BINDING_LENGTH);
            text(expression, MAX_FRAGMENT_LENGTH);
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
        text(stopCondition, MAX_FRAGMENT_LENGTH);
        text(resultExpression, MAX_FRAGMENT_LENGTH);
        Objects.requireNonNull(updateMode);
        Objects.requireNonNull(evaluationOrder);
        Objects.requireNonNull(termination);
        if (states.isEmpty() || states.size() != updates.size()) {
            throw new IllegalArgumentException("Every state needs exactly one update");
        }
        var bindings = new HashSet<String>();
        var names = new HashSet<String>();
        for (State state : states) {
            if (!bindings.add(state.bindingKey()) || !names.add(state.name())) {
                throw new IllegalArgumentException("State identities and names must be distinct");
            }
        }
        if (!bindings.contains(termination.counterBinding())) {
            throw new IllegalArgumentException("The counter must be a declared state");
        }
        for (Update update : updates) {
            if (!bindings.remove(update.targetBinding())) {
                throw new IllegalArgumentException("Unknown or duplicate update target");
            }
        }
    }

    /** Make the immutable boundary explicit to callers and bytecode analyzers. */
    @Override public List<State> states() { return List.copyOf(states); }
    @Override public List<Update> updates() { return List.copyOf(updates); }

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
