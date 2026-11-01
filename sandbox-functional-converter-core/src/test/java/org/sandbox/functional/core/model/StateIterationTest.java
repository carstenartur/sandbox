/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StateIterationTest {
    private static final StateIteration.State N = new StateIteration.State("n-key", "n", "int");
    private static final StateIteration.State A = new StateIteration.State("a-key", "a", "long");
    private static final StateIteration.Update NEXT_N = new StateIteration.Update("n-key", "n - 1");
    private static final StateIteration.Update NEXT_A = new StateIteration.Update("a-key", "a + n");

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
        var model = model(List.of(N, A), List.of(NEXT_N, NEXT_A), Set.of());
        assertEquals(StateIteration.UpdateMode.SIMULTANEOUS, model.updateMode());
        assertEquals(StateIteration.EvaluationOrder.LEFT_TO_RIGHT, model.evaluationOrder());
        assertEquals("a + n", model.updates().get(1).expression());
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
                () -> model(List.of(N, A), List.of(NEXT_N, new StateIteration.Update("unknown", "a")), Set.of()));
    }

    @Test void rejectsAnUndeclaredCounterAndNondecreasingCountdown() {
        assertThrows(IllegalArgumentException.class,
                () -> model(List.of(A), List.of(NEXT_A), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(new StateIteration.CountDown("n-key", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(new StateIteration.CountDown("n-key", -1, 0)));
    }

    @Test void rejectsMissingOrOversizedSourceFragments() {
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(new StateIteration.Update("n-key", " ")));
        assertThrows(IllegalArgumentException.class,
                () -> assertNotNull(new StateIteration.Update("n-key", "x".repeat(StateIteration.MAX_FRAGMENT_LENGTH + 1))));
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(new StateIteration.State("", "n", "int")));
    }

    private static StateIteration model(List<StateIteration.State> states,
            List<StateIteration.Update> updates, Set<StateIteration.Obligation> obligations) {
        return new StateIteration(states, "n <= 0", updates, "a", StateIteration.UpdateMode.SIMULTANEOUS,
                StateIteration.EvaluationOrder.LEFT_TO_RIGHT,
                new StateIteration.CountDown("n-key", 1, 0), obligations);
    }
}
