/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.sandbox.functional.core.model.UlrExpression.BinaryOperator.*;
import static org.sandbox.functional.core.model.UlrExpression.Overflow.*;
import static org.sandbox.functional.core.model.UlrExpression.Type.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StateIterationTest {
    private static final StateIteration.State N = new StateIteration.State("n-key", "n", INT32);
    private static final StateIteration.State A = new StateIteration.State("a-key", "a", INT64);
    private static final UlrExpression.Read READ_N = new UlrExpression.Read("n-key", INT32);
    private static final UlrExpression.Read READ_A = new UlrExpression.Read("a-key", INT64);
    private static final UlrExpression.Binary STOP = new UlrExpression.Binary(LESS_OR_EQUAL, READ_N,
            new UlrExpression.Constant(BigInteger.ZERO, INT32), BOOLEAN, EXACT);
    private static final StateIteration.Update NEXT_N = new StateIteration.Update("n-key",
            new UlrExpression.Binary(SUBTRACT, READ_N, new UlrExpression.Constant(BigInteger.ONE, INT32), INT32, WRAP));
    private static final StateIteration.Update NEXT_A = new StateIteration.Update("a-key",
            new UlrExpression.Binary(ADD, READ_A, READ_N, INT64, WRAP));

    @Test void capturesTheStateUpdatesAndObligationsByValue() {
        var states = new ArrayList<>(List.of(N, A));
        var updates = new ArrayList<>(List.of(NEXT_N, NEXT_A));
        var obligations = new HashSet<>(Set.of(StateIteration.Obligation.STACK_BEHAVIOR_CHANGE));
        var model = model(states, updates, obligations);
        states.clear(); updates.clear(); obligations.clear();
        assertEquals(List.of(N, A), model.states());
        assertEquals(List.of(NEXT_N, NEXT_A), model.updates());
        assertTrue(model.obligations().contains(StateIteration.Obligation.STACK_BEHAVIOR_CHANGE));
        assertThrows(UnsupportedOperationException.class, () -> model.states().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.updates().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.obligations().clear());
    }

    @Test void recordsArgumentEvaluationSeparatelyFromSimultaneousAssignment() {
        var model = model(List.of(N, A), List.of(NEXT_A, NEXT_N), Set.of());
        assertEquals(StateIteration.UpdateMode.SIMULTANEOUS, model.updateMode());
        assertEquals(StateIteration.EvaluationOrder.LEFT_TO_RIGHT, model.evaluationOrder());
        assertEquals(List.of(NEXT_A, NEXT_N), model.updates());
        assertEquals("n-key", model.termination().counterBinding());
    }

    @Test void rejectsDuplicateMissingAndUnknownTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> model(List.of(N, N), List.of(NEXT_N, NEXT_N), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> model(List.of(N, A), List.of(NEXT_N), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> model(List.of(N, A), List.of(NEXT_N, NEXT_N), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> model(List.of(N, A), List.of(NEXT_N, new StateIteration.Update("unknown", READ_A)), Set.of()));
    }

    @Test void rejectsAnUndeclaredCounterAndNondecreasingCountdown() {
        assertThrows(IllegalArgumentException.class,
                () -> model(List.of(A), List.of(NEXT_A), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(new StateIteration.CountDown("n-key", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(new StateIteration.CountDown("n-key", -1, 0)));
    }

    @Test void rejectsReadsOutsideTheDeclaredStateAndIncorrectWidths() {
        assertThrows(IllegalArgumentException.class, () -> model(List.of(N, A),
                List.of(NEXT_N, new StateIteration.Update("a-key", new UlrExpression.Read("outside", INT64))), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> model(List.of(N, A),
                List.of(NEXT_N, new StateIteration.Update("a-key", new UlrExpression.Read("n-key", INT64))), Set.of()));
    }

    @Test void permitsWideningButRejectsNarrowingAtStateAndResultBoundaries() {
        var updates = List.of(NEXT_N, new StateIteration.Update("a-key", READ_N));
        assertDoesNotThrow(() -> model(List.of(N, A), updates, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> model(List.of(N, A),
                List.of(new StateIteration.Update("n-key", READ_A), NEXT_A), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new StateIteration(List.of(N, A), STOP, updates, READ_A,
                INT32, StateIteration.UpdateMode.SIMULTANEOUS, StateIteration.EvaluationOrder.LEFT_TO_RIGHT,
                new StateIteration.CountDown("n-key", 1, 0), Set.of()));
    }

    @Test void requiresABooleanStopCondition() {
        assertThrows(IllegalArgumentException.class, () -> new StateIteration(List.of(N, A), READ_N,
                List.of(NEXT_N, NEXT_A), READ_A, INT64, StateIteration.UpdateMode.SIMULTANEOUS,
                StateIteration.EvaluationOrder.LEFT_TO_RIGHT, new StateIteration.CountDown("n-key", 1, 0), Set.of()));
    }

    @Test void rejectsMissingOrOversizedStateIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new StateIteration.Update(" ", READ_N));
        assertThrows(IllegalArgumentException.class,
                () -> new StateIteration.Update("x".repeat(4_097), READ_N));
        assertThrows(IllegalArgumentException.class, () -> new StateIteration.State("", "n", INT32));
    }

    private static StateIteration model(List<StateIteration.State> states,
            List<StateIteration.Update> updates, Set<StateIteration.Obligation> obligations) {
        return new StateIteration(states, STOP, updates, READ_A, INT64, StateIteration.UpdateMode.SIMULTANEOUS,
                StateIteration.EvaluationOrder.LEFT_TO_RIGHT,
                new StateIteration.CountDown("n-key", 1, 0), obligations);
    }
}
