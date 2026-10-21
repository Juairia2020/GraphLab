package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class NeedModelTest {
    @Test
    public void noHistoryMeansEqualNeed() {
        NeedModel m = NeedModel.neutral();
        assertEquals(m.need(QuestionKind.NEXT_STEP), m.need(QuestionKind.TRUE_FALSE), 1e-12);
    }

    @Test
    public void errorRateUsesAPrior() {
        assertEquals(0.5, NeedModel.errorRate(null), 1e-12);
        assertEquals(0.5, NeedModel.errorRate(new int[] {0, 0}), 1e-12);
        assertEquals(1 - 11.0 / 12, NeedModel.errorRate(new int[] {10, 10}), 1e-12);
    }

    @Test
    public void weakActivitiesNeedMoreAndPlayedOnesLessNovelty() {
        NeedModel m = new NeedModel(Map.of("true-false", new int[] {20, 2}, "best-tool", new int[] {20, 19}), Map.of());
        assertTrue(m.need(QuestionKind.TRUE_FALSE) > m.need(QuestionKind.BEST_TOOL));
        assertTrue("unplayed beats well-played and right", m.need(QuestionKind.NEXT_STEP) > m.need(QuestionKind.BEST_TOOL));
    }

    @Test
    public void weakTopicRaisesNeed() {
        NeedModel weak = new NeedModel(Map.of(), Map.of("dijkstra", new int[] {10, 1}));
        assertTrue(weak.need(QuestionKind.NEXT_STEP) > NeedModel.neutral().need(QuestionKind.NEXT_STEP));
    }

    @Test
    public void textRoundTrip() {
        NeedModel m = new NeedModel(Map.of("true-false", new int[] {20, 2}), Map.of("dijkstra", new int[] {4, 1}));
        NeedModel back = NeedModel.parse(m.encode());
        for (QuestionKind k : QuestionKind.values()) {
            assertEquals(m.need(k), back.need(k), 1e-12);
        }
        assertEquals(NeedModel.neutral().need(QuestionKind.NEXT_STEP), NeedModel.parse("junk,a:x=9/99,a:y=q/1,").need(QuestionKind.NEXT_STEP), 1e-12);
        assertEquals("", NeedModel.neutral().encode());
        assertEquals(NeedModel.neutral().need(QuestionKind.TRUE_FALSE), NeedModel.parse(null).need(QuestionKind.TRUE_FALSE), 1e-12);
    }

    @Test
    public void impossibleStatsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new NeedModel(Map.of("x", new int[] {1, 2}), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new NeedModel(Map.of("x", new int[] {1}), Map.of()));
    }
}
