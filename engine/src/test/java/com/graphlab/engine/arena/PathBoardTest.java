package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Graph;
import java.util.List;
import org.junit.Test;

public class PathBoardTest {
    // 0-1 (2), 1-2 (2), 0-2 (9), 2-3 (1), 1-3 (7)
    private static Graph graph() {
        return Graph.builder(false).nodes(4).edge(0, 0, 1, 2).edge(1, 1, 2, 2).edge(2, 0, 2, 9).edge(3, 2, 3, 1).edge(4, 1, 3, 7).build();
    }

    private static Task shortest() {
        return new Task(Interaction.PATH, List.of(), List.of(0, 1, 3), 0, 0, "A to D");
    }

    @Test
    public void tapsAddEdgesAlongRealEdgesOnly() {
        PathBoard board = new PathBoard(graph(), 0, 3);
        assertEquals(List.of(0), board.nodes());
        assertEquals(PathBoard.Result.IGNORED, board.tap(3));   // 0 and 3 are not joined
        assertEquals(PathBoard.Result.ADDED, board.tap(1));
        assertEquals(PathBoard.Result.ADDED, board.tap(2));
        assertEquals(List.of(0, 1), board.edges());
        assertFalse(board.isComplete());
        assertEquals(PathBoard.Result.ADDED, board.tap(3));
        assertTrue(board.isComplete());
        assertEquals(List.of(0, 1, 3), board.edges());
        assertEquals(5.0, board.weight(), 1e-9);
        assertEquals(0, board.mistakes(shortest()));
    }

    @Test
    public void tappingTheLastNodeTakesItBackButNeverTheStart() {
        PathBoard board = new PathBoard(graph(), 0, 3);
        board.tap(1);
        assertEquals(PathBoard.Result.REMOVED, board.tap(1));
        assertEquals(List.of(0), board.nodes());
        assertEquals(PathBoard.Result.IGNORED, board.tap(0));
    }

    @Test
    public void noWalkingInCirclesAndNoUnknownNodes() {
        PathBoard board = new PathBoard(graph(), 0, 3);
        board.tap(1);
        board.tap(2);
        assertEquals(PathBoard.Result.IGNORED, board.tap(0));
        assertEquals(PathBoard.Result.IGNORED, board.tap(42));
    }

    @Test
    public void aLongerRouteHasMistakes() {
        PathBoard board = new PathBoard(graph(), 0, 3);
        board.tap(1);
        board.tap(3);   // 0-1, 1-3 = 9 > 5
        assertTrue(board.isComplete());
        assertTrue(board.mistakes(shortest()) > 0);
        board.reset();
        assertEquals(List.of(0), board.nodes());
        assertEquals(3, board.mistakes(shortest()));
    }

    @Test
    public void badConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new PathBoard(graph(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PathBoard(graph(), 0, 9));
    }
}
