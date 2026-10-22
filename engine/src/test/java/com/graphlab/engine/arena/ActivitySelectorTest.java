package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class ActivitySelectorTest {
    private static final List<QuestionKind> KINDS =
            List.of(QuestionKind.TRUE_FALSE, QuestionKind.BEST_TOOL, QuestionKind.NEXT_STEP);

    @Test
    public void neverRepeatsThePreviousActivity() {
        Random random = new Random(3);
        QuestionKind previous = null;
        for (int i = 0; i < 500; i++) {
            QuestionKind k = ActivitySelector.next(KINDS, NeedModel.neutral(), previous, random, 0.5);
            assertTrue(k != previous);
            previous = k;
        }
    }

    @Test
    public void weakActivityIsChosenMoreOften() {
        NeedModel m = new NeedModel(Map.of("true-false", new int[] {30, 3}, "best-tool", new int[] {30, 29},
                "next-step", new int[] {30, 29}), Map.of());
        Random random = new Random(5);
        Map<QuestionKind, Integer> count = new EnumMap<>(QuestionKind.class);
        for (int i = 0; i < 3000; i++) {
            count.merge(ActivitySelector.next(KINDS, m, null, random, 0.5), 1, Integer::sum);
        }
        assertTrue(count.toString(), count.get(QuestionKind.TRUE_FALSE) > count.get(QuestionKind.BEST_TOOL) * 2);
        assertTrue("others still appear", count.get(QuestionKind.BEST_TOOL) > 0 && count.get(QuestionKind.NEXT_STEP) > 0);
    }

    @Test
    public void sameSeedSameChoice() {
        assertEquals(ActivitySelector.next(KINDS, NeedModel.neutral(), null, new Random(9), 0.5),
                ActivitySelector.next(KINDS, NeedModel.neutral(), null, new Random(9), 0.5));
    }

    @Test
    public void aSingleChoiceIsAllowedEvenIfItIsThePrevious() {
        assertEquals(QuestionKind.TRUE_FALSE, ActivitySelector.next(List.of(QuestionKind.TRUE_FALSE),
                NeedModel.neutral(), QuestionKind.TRUE_FALSE, new Random(1), 0.5));
    }

    @Test
    public void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ActivitySelector.next(List.of(), NeedModel.neutral(), null, new Random(), 0.5));
        assertThrows(IllegalArgumentException.class, () -> ActivitySelector.next(KINDS, NeedModel.neutral(), null, new Random(), 0));
    }
}
