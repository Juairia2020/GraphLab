package com.graphlab.engine.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Checks the library that ships inside the app (app/src/main/assets/sample-library.json): it parses,
 * the copy in docs/ is identical, and every promise a description makes is true when the real
 * algorithm runs on that graph.
 */
public class SampleLibraryContentTest {
    private static final Path ASSET = Path.of("..", "app", "src", "main", "assets", "sample-library.json");
    private static final Path DOCS = Path.of("..", "docs", "sample-library.json");
    private static final Map<String, Graph> GRAPHS = new HashMap<>();

    @BeforeClass
    public static void load() throws Exception {
        assumeTrue("the asset file is not next to the engine module", Files.exists(ASSET));
        for (SampleLibrary.Entry entry : SampleLibrary.parse(Files.readString(ASSET, StandardCharsets.UTF_8))) {
            GRAPHS.put(entry.id(), entry.document().graph());
        }
    }

    private static int idOf(String sample, String label) {
        for (Node node : GRAPHS.get(sample).nodes()) {
            if (node.label().equals(label)) {
                return node.id();
            }
        }
        throw new AssertionError("no node " + label + " in " + sample);
    }

    @Test
    public void theDocsCopyIsIdenticalToTheAsset() throws Exception {
        assumeTrue(Files.exists(DOCS));
        assertEquals(Files.readString(ASSET, StandardCharsets.UTF_8), Files.readString(DOCS, StandardCharsets.UTF_8));
    }

    @Test
    public void hasTheNineSamples() {
        assertEquals(9, GRAPHS.size());
        assertTrue(GRAPHS.keySet().containsAll(List.of("road-network", "cities-bangladesh", "flow-textbook", "negative-edge",
                "odd-cycle", "two-clusters", "complete-k5", "two-islands", "binary-tree")));
    }

    @Test
    public void roadNetworkShortestRouteFromAToEIs20() {
        Graph g = GRAPHS.get("road-network");
        Result r = Algorithms.byId("dijkstra").run(g, Params.from(idOf("road-network", "A"))).result();
        assertEquals(20.0, r.distances().get(idOf("road-network", "E")), 0);
    }

    @Test
    public void citiesAreConnectedAndKhulnaToSylhetIs510() {
        Graph g = GRAPHS.get("cities-bangladesh");
        Result bfs = Algorithms.byId("bfs").run(g, Params.from(idOf("cities-bangladesh", "DHK"))).result();
        assertEquals(7, bfs.visitOrder().size());
        Result d = Algorithms.byId("dijkstra").run(g, Params.from(idOf("cities-bangladesh", "KHL"))).result();
        assertEquals(510.0, d.distances().get(idOf("cities-bangladesh", "SYL")), 0);
    }

    @Test
    public void textbookFlowIs23() {
        Graph g = GRAPHS.get("flow-textbook");
        Params p = Params.from(idOf("flow-textbook", "S")).withTarget(idOf("flow-textbook", "T"));
        assertEquals(23.0, Algorithms.byId("edmonds-karp").run(g, p).result().value(), 0);
    }

    @Test
    public void theNegativeEdgeStopsDijkstraWithAHintAndBellmanFordFindsZero() {
        Graph g = GRAPHS.get("negative-edge");
        int s = idOf("negative-edge", "S");
        int a = idOf("negative-edge", "A");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Algorithms.byId("dijkstra").run(g, Params.from(s)));
        assertTrue(refused.getMessage(), refused.getMessage().contains("Bellman-Ford"));
        Result bellman = Algorithms.byId("bellman-ford").run(g, Params.from(s)).result();
        assertEquals(0.0, bellman.distances().get(a), 0);
        assertFalse(bellman.negativeCycle());
    }

    @Test
    public void theOddCycleNeedsThreeColours() {
        Result r = Algorithms.byId("graph-colouring").run(GRAPHS.get("odd-cycle"), Params.none()).result();
        assertEquals(3, Collections.max(r.colours().values()).intValue());
    }

    @Test
    public void theBridgeIsTheMinimumCutAndKargerCanFindIt() {
        Graph g = GRAPHS.get("two-clusters");
        int smallest = Integer.MAX_VALUE;
        for (long seed = 1; seed <= 60; seed++) {
            smallest = Math.min(smallest, Algorithms.byId("karger").run(g, Params.none().withSeed(seed)).result().cutEdgeIds().size());
        }
        assertEquals(1, smallest);
    }

    @Test
    public void k5SpanningTreeUsesFourEdges() {
        Result r = Algorithms.byId("kruskal").run(GRAPHS.get("complete-k5"), Params.none()).result();
        assertEquals(4, r.treeEdgeIds().size());
        assertEquals(10, GRAPHS.get("complete-k5").edgeCount());
    }

    @Test
    public void twoIslandsReachOnlyOneIslandFromA() {
        Result r = Algorithms.byId("bfs").run(GRAPHS.get("two-islands"), Params.from(idOf("two-islands", "A"))).result();
        assertEquals(3, r.visitOrder().size());
        assertFalse(r.visitOrder().contains(idOf("two-islands", "D")));
    }

    @Test
    public void breadthFirstAndDepthFirstVisitTheTreeInDifferentOrders() {
        Graph g = GRAPHS.get("binary-tree");
        int a = idOf("binary-tree", "A");
        List<Integer> bfs = Algorithms.byId("bfs").run(g, Params.from(a)).result().visitOrder();
        List<Integer> dfs = Algorithms.byId("dfs").run(g, Params.from(a)).result().visitOrder();
        assertEquals(7, bfs.size());
        assertNotEquals(bfs, dfs);
    }
}
