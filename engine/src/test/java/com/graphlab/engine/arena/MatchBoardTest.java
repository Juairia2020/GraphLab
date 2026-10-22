package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MatchBoardTest {
    private static final Task TASK = new Task(Interaction.MATCH,
            List.of("BFS", "hops", "Kruskal", "sorted edges", "Dijkstra", "no negatives"), List.of(), 3, 0, "x");

    private static int positionOf(MatchBoard board, String text) {
        for (int i = 0; i < board.size(); i++) {
            if (board.text(i).equals(text)) {
                return i;
            }
        }
        throw new AssertionError(text);
    }

    @Test
    public void sameSeedSameBoardAndAllTilesPresent() {
        MatchBoard a = new MatchBoard(TASK, 7);
        MatchBoard b = new MatchBoard(TASK, 7);
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.text(i), b.text(i));
            shown.add(a.text(i));
        }
        assertEquals(6, shown.size());
        assertTrue(shown.containsAll(TASK.items()));
        boolean differs = false;
        for (long seed = 0; seed < 20; seed++) {
            MatchBoard other = new MatchBoard(TASK, seed);
            for (int i = 0; i < other.size(); i++) {
                differs |= !other.text(i).equals(a.text(i));
            }
        }
        assertTrue("seeds should give different boards", differs);
    }

    @Test
    public void aPairStaysUp() {
        MatchBoard board = new MatchBoard(TASK, 1);
        int a = positionOf(board, "BFS");
        int b = positionOf(board, "hops");
        assertEquals(MatchBoard.Result.FIRST, board.flip(a));
        assertTrue(board.isFaceUp(a));
        assertEquals(MatchBoard.Result.MATCH, board.flip(b));
        assertTrue(board.isMatched(a) && board.isMatched(b));
        assertEquals(0, board.mistakes());
        assertFalse(board.isDone());
    }

    @Test
    public void aWrongPairIsCountedAndBlocksUntilFlippedBack() {
        MatchBoard board = new MatchBoard(TASK, 1);
        int a = positionOf(board, "BFS");
        int b = positionOf(board, "sorted edges");
        board.flip(a);
        assertEquals(MatchBoard.Result.MISMATCH, board.flip(b));
        assertEquals(1, board.mistakes());
        assertNotNull(board.waitingPair());
        assertEquals(MatchBoard.Result.IGNORED, board.flip(positionOf(board, "Kruskal")));
        board.flipBack();
        assertNull(board.waitingPair());
        assertFalse(board.isFaceUp(a));
        assertFalse(board.isFaceUp(b));
    }

    @Test
    public void ignoresRepeatedAndOutOfRangeFlips() {
        MatchBoard board = new MatchBoard(TASK, 1);
        board.flip(0);
        assertEquals(MatchBoard.Result.IGNORED, board.flip(0));
        assertEquals(MatchBoard.Result.IGNORED, board.flip(-1));
        assertEquals(MatchBoard.Result.IGNORED, board.flip(99));
        assertEquals(0, board.mistakes());
    }

    @Test
    public void finishingWithNoMistakesIsCorrect() {
        MatchBoard board = new MatchBoard(TASK, 3);
        for (int i = 0; i < TASK.items().size(); i += 2) {
            board.flip(positionOf(board, TASK.items().get(i)));
            assertEquals(MatchBoard.Result.MATCH, board.flip(positionOf(board, TASK.items().get(i + 1))));
        }
        assertTrue(board.isDone());
        assertTrue(TASK.isCorrect(TASK.mistakes(board.mistakes())));
    }

    @Test
    public void tooManyMistakesFail() {
        MatchBoard board = new MatchBoard(TASK, 3);
        int guard = 0;
        while (board.mistakes() < 4 && guard++ < 50) {
            board.flip(positionOf(board, "BFS"));
            board.flip(positionOf(board, "sorted edges"));
            board.flipBack();
        }
        assertEquals(4, board.mistakes());
        assertFalse(TASK.isCorrect(TASK.mistakes(board.mistakes())));
    }

    @Test(expected = IllegalArgumentException.class)
    public void onlyAMatchTaskIsAccepted() {
        new MatchBoard(new Task(Interaction.NUMBER, List.of(), List.of(1), 0, 0, "x"), 1);
    }
}
