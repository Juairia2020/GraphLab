package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class ArenaRoundTest {

    private static ArenaRound round() {
        return ArenaRound.of(QuestionKind.DIJKSTRA_DISTANCE, Level.MEDIUM, 11L);
    }

    private static int wrongChoice(Question q) {
        return (q.correctIndex() + 1) % q.options().size();
    }

    @Test
    public void aRoundHasABasicAndAHardQuestionOfTheChosenKind() {
        ArenaRound r = round();
        assertEquals(2, r.size());
        assertEquals(QuestionKind.DIJKSTRA_DISTANCE, r.kind());
        assertFalse(r.isMixed());
        for (int i = 0; i < 2; i++) {
            assertEquals(QuestionKind.DIJKSTRA_DISTANCE, r.question(i).kind());
        }
        assertEquals(Level.MEDIUM, r.question(0).level());
        assertEquals(Level.HARD, r.question(1).level());
    }

    @Test
    public void sameSeedSameRound() {
        ArenaRound a = round();
        ArenaRound b = round();
        for (int i = 0; i < 2; i++) {
            assertEquals(a.question(i), b.question(i));
        }
        ArenaRound c = ArenaRound.of(QuestionKind.DIJKSTRA_DISTANCE, Level.MEDIUM, 12L);
        assertTrue(!a.question(0).equals(c.question(0)));
    }

    @Test
    public void questionsInARoundAreDifferent() {
        for (QuestionKind kind : QuestionKind.values()) {
            ArenaRound r = ArenaRound.of(kind, Level.EASY, 5L);
            Set<Question> seen = new HashSet<>();
            for (int i = 0; i < 2; i++) {
                seen.add(r.question(i));
            }
            assertEquals(kind.toString(), 2, seen.size());
        }
    }

    @Test
    public void aTwoQuestionRoundScoresStarsSensibly() {
        assertEquals(0, ArenaScoring.stars(0, 2));
        assertEquals(1, ArenaScoring.stars(1, 2));
        assertEquals(3, ArenaScoring.stars(2, 2));
    }

    @Test
    public void mixedRoundNeverRepeatsAnActivityInARowAndMixesSeveral() {
        for (long seed = 0; seed < 60; seed++) {
            ArenaRound r = ArenaRound.mixed(Level.EASY, seed);
            assertTrue(r.isMixed());
            assertNull(r.kind());
            Set<QuestionKind> kinds = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                kinds.add(r.question(i).kind());
                if (i > 0) {
                    assertTrue("seed " + seed + " position " + i, r.question(i).kind() != r.question(i - 1).kind());
                }
            }
            assertTrue("seed " + seed, kinds.size() >= 3);
        }
    }

    @Test
    public void answeringScoresAndAdvances() {
        ArenaRound r = round();
        ArenaRound.Verdict first = r.answer(r.current().correctIndex());
        assertTrue(first.correct());
        assertEquals(20, first.points());
        assertTrue(r.isAnswered());
        assertFalse(r.isFinished());
        r.advance();
        assertEquals(1, r.index());
        assertFalse(r.isAnswered());
        ArenaRound.Verdict second = r.answer(wrongChoice(r.current()));
        assertFalse(second.correct());
        assertEquals(0, second.points());
        assertEquals(r.current().correctIndex(), second.correctIndex());
        assertEquals(r.current().explanation(), second.explanation());
        assertEquals(1, r.correctCount());
        assertEquals(2, r.answeredCount());
        assertEquals(20, r.points());
    }

    @Test
    public void aPerfectRoundGivesThreeStarsAndFullPoints() {
        ArenaRound r = ArenaRound.of(QuestionKind.BFS_ORDER, Level.HARD, 3L);
        for (int i = 0; i < 2; i++) {
            r.answer(r.current().correctIndex());
            if (r.hasNext()) {
                r.advance();
            }
        }
        assertTrue(r.isFinished());
        assertEquals(2, r.correctCount());
        assertEquals(60, r.points());   // two right answers at Hard: 2 x 30
        assertEquals(3, r.stars());
    }

    @Test
    public void aRoundOfWrongAnswersGivesNoStars() {
        ArenaRound r = round();
        for (int i = 0; i < 2; i++) {
            r.answer(wrongChoice(r.current()));
            if (r.hasNext()) {
                r.advance();
            }
        }
        assertTrue(r.isFinished());
        assertEquals(0, r.correctCount());
        assertEquals(0, r.stars());
        assertEquals(0, r.points());
    }

    @Test
    public void callsInTheWrongOrderAreRefused() {
        ArenaRound r = round();
        try {
            r.advance();
            fail("advance before answering");
        } catch (IllegalStateException expected) {
            // fine
        }
        r.answer(0);
        try {
            r.answer(0);
            fail("answered twice");
        } catch (IllegalStateException expected) {
            // fine
        }
        for (int i = 1; i < 2; i++) {
            r.advance();
            r.answer(0);
        }
        try {
            r.advance();
            fail("advance past the end");
        } catch (IllegalStateException expected) {
            // fine
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void anOptionThatDoesNotExistIsRefused() {
        round().answer(4);
    }

    @Test
    public void restoreReplaysAnswersAndPosition() {
        ArenaRound original = round();
        original.answer(original.current().correctIndex());
        original.advance();
        original.answer(wrongChoice(original.current()));   // second question answered, feedback still showing

        ArenaRound copy = round();
        copy.restore(original.choices(), original.index());
        assertEquals(original.index(), copy.index());
        assertEquals(original.answeredCount(), copy.answeredCount());
        assertEquals(original.correctCount(), copy.correctCount());
        assertTrue(copy.isAnswered());
        assertEquals(original.current(), copy.current());
        assertFalse(copy.hasNext());   // it was the last question
    }

    @Test
    public void restoreWithTheNextQuestionShowing() {
        ArenaRound original = round();
        original.answer(0);
        original.advance();
        ArenaRound copy = round();
        copy.restore(original.choices(), original.index());
        assertEquals(1, copy.index());
        assertFalse(copy.isAnswered());
    }

    @Test(expected = IllegalArgumentException.class)
    public void restoreRefusesAnImpossiblePosition() {
        round().restore(new int[] {0}, 3);
    }

    @Test
    public void restoreOfAFinishedRoundStaysOnTheLastQuestion() {
        ArenaRound original = round();
        for (int i = 0; i < 2; i++) {
            original.answer(0);
            if (original.hasNext()) {
                original.advance();
            }
        }
        ArenaRound copy = round();
        copy.restore(original.choices(), original.index());
        assertTrue(copy.isFinished());
        assertEquals(1, copy.index());
        assertEquals(original.points(), copy.points());
    }
}
