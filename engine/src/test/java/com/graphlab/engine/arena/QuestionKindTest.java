package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.trace.Algorithm;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class QuestionKindTest {

    @Test
    public void idsAreUniqueAndLookUpBothWays() {
        Set<String> ids = new HashSet<>();
        for (QuestionKind kind : QuestionKind.values()) {
            assertTrue("unique id " + kind.id(), ids.add(kind.id()));
            assertEquals(kind, QuestionKind.byId(kind.id()));
            assertEquals(kind, QuestionKind.findById(kind.id()));
        }
        assertNull(QuestionKind.findById("nope"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void byIdRefusesAnUnknownId() {
        QuestionKind.byId("nope");
    }

    @Test
    public void everyKindUsesARealAlgorithmWithTheSameInputKind() {
        for (QuestionKind kind : QuestionKind.values()) {
            assertTrue(kind.title().length() > 3 && kind.summary().length() > 10);
            if (kind.fromCards()) {
                assertNull(kind.algorithm());
                assertNull(kind.category());
                continue;
            }
            Algorithm algorithm = Algorithms.byId(kind.algorithmId());
            assertEquals(kind.toString(), algorithm.inputKind(), kind.inputKind());
            assertEquals(algorithm.category(), kind.category());
        }
    }

    @Test
    public void thirteenOfFifteenAlgorithmsHaveAnActivity() {
        Set<String> covered = new HashSet<>();
        for (QuestionKind kind : QuestionKind.values()) {
            if (!kind.fromCards()) {
                covered.add(kind.algorithmId());
            }
        }
        assertEquals(13, covered.size());
        Set<String> left = new HashSet<>();
        for (Algorithm a : Algorithms.all()) {
            if (!covered.contains(a.id())) {
                left.add(a.id());
            }
        }
        assertEquals("only the random algorithms, the MS12 DP pack (reached through Fill the table) A* (MS15, Lab and map only) and the eight MS18 algorithms have no activity of their own",
                Set.of("karger", "randomized-quick-sort", "coin-change", "lcs", "edit-distance", "lis", "astar", "topological-sort", "tarjan-scc",
                        "dinic", "bipartite-matching", "huffman", "closest-pair", "reservoir-sampling", "matrix-chain"), left);
    }

    @Test
    public void forAlgorithmFindsEveryActivityOfOne() {
        assertEquals(List.of(QuestionKind.DFS_ORDER, QuestionKind.DFS_FINISH), QuestionKind.forAlgorithm("dfs"));
        assertEquals(List.of(), QuestionKind.forAlgorithm("karger"));
    }

    @Test
    public void levelsGrowAndLinkUp() {
        assertNull(Level.EASY.previous());
        assertNull(Level.HARD.next());
        assertEquals(Level.MEDIUM, Level.EASY.next());
        assertEquals(Level.MEDIUM, Level.HARD.previous());
        Level before = null;
        for (Level level : Level.values()) {
            assertTrue(level.minNodes() <= level.maxNodes() && level.minExtraEdges() <= level.maxExtraEdges());
            assertTrue(level.minItems() <= level.maxItems() && level.minCapacity() <= level.maxCapacity());
            if (before != null) {
                assertTrue(level.maxNodes() > before.maxNodes() && level.listSize() > before.listSize());
                assertEquals(before.multiplier() + 1, level.multiplier());
            }
            before = level;
        }
    }
}
