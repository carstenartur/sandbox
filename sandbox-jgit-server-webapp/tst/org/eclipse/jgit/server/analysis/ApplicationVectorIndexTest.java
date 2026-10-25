/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ApplicationVectorIndexTest {
  @Test public void ranksByCosineThenIdentityAndBoundsResults() {
    var index = ApplicationVectorIndex.build(Map.of("b", vector(2, 0), "a", vector(1, 0), "orthogonal", vector(0, 1),
        "opposite", vector(-1, 0)));
    assertEquals(List.of("a", "b"), ids(index.nearest(vector(10, 0), 2)));
    assertEquals(List.of("a", "b", "orthogonal", "opposite"), ids(index.nearest(vector(1, 0), 10)));
    assertEquals(1, index.nearest(vector(1, 0), 1).getFirst().score(), .000001);
    assertThrows(IllegalArgumentException.class, () -> index.nearest(vector(1, 0), 0));
  }

  @Test public void copiesCallerVectorsAndSimilarSearchUsesTheStoredSeedWithoutAModel() {
    var original = vector(1, 0);
    var index = ApplicationVectorIndex.build(Map.of("seed", original, "neighbor", vector(1, 0), "other", vector(0, 1)));
    original[0] = 0; original[1] = 1;
    assertEquals(List.of("neighbor", "other"), ids(index.similar("seed", 5)));
    assertEquals(List.of(), index.similar("unknown", 5));
  }

  @Test public void rejectsInvalidVectorsWithoutPoisoningValidSearchOrOverflowingNorms() {
    var nan = vector(Float.NaN, 1); var infinity = vector(Float.POSITIVE_INFINITY, 1);
    var index = ApplicationVectorIndex.build(Map.of("nan", nan, "infinite", infinity, "zero", new float[384],
        "wrong-size", new float[2], "huge", vector(Float.MAX_VALUE, 0)));
    assertEquals(4, index.rejectedVectors()); assertEquals(1, index.size());
    assertEquals(List.of("huge"), ids(index.nearest(vector(Float.MAX_VALUE, 0), 3)));
    assertTrue(index.nearest(nan, 3).isEmpty()); assertTrue(index.nearest(new float[2], 3).isEmpty());
  }

  @Test public void rankFusionUsesStableDocumentIdentityAndSemanticFirstTies() {
    record Document(String id, String label) {}
    var a = new Document("a", "semantic a"); var b = new Document("b", "semantic b");
    var textA = new Document("a", "text a"); var c = new Document("c", "text c");
    assertEquals(List.of(a, c, b), ApplicationVectorIndex.fuse(List.of(a, b), List.of(c, textA), 3, Document::id));
    assertEquals(List.of(a, c), ApplicationVectorIndex.fuse(List.of(a), List.of(c), 2, Document::id));
    assertEquals(List.of(c), ApplicationVectorIndex.fuse(List.of(), List.of(c), 2, Document::id));
  }

  private static List<String> ids(List<ApplicationVectorIndex.Hit> hits) {
    return hits.stream().map(ApplicationVectorIndex.Hit::id).toList();
  }
  private static float[] vector(float first, float second) {
    var result = new float[384]; result[0] = first; result[1] = second; return result;
  }
}
