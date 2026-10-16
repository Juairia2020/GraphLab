package com.graphlab.engine.preset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphlab.engine.content.ContentException;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PresetTest {
    private static final double EPS = 1e-9;

    private static Preset preset(String id) {
        Preset p = PresetBank.current().byId(id);
        assertNotNull(id, p);
        return p;
    }

    /** Min cut by trying every set of nodes that holds the source and not the sink: independent of the flow code. */
    private static double bruteMinCut(Preset p) {
        Graph g = p.graph();
        int n = g.nodeCount();
        double best = Double.MAX_VALUE;
        for (int mask = 0; mask < (1 << n); mask++) {
            if ((mask >> p.source() & 1) == 0 || (mask >> p.target() & 1) == 1) {
                continue;
            }
            double sum = 0;
            for (Edge e : g.edges()) {
                if ((mask >> e.from() & 1) == 1 && (mask >> e.to() & 1) == 0) {
                    sum += e.weight();
                }
            }
            best = Math.min(best, sum);
        }
        return best;
    }

    private static boolean connected(Graph g, int[] chosen) {
        int n = g.nodeCount();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        int comps = n;
        for (int idx : chosen) {
            Edge e = g.edges().get(idx);
            int a = find(parent, e.from());
            int b = find(parent, e.to());
            if (a != b) {
                parent[a] = b;
                comps--;
            }
        }
        return comps == 1;
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) {
            x = p[x];
        }
        return x;
    }

    private static double bruteMst(Graph g) {
        int m = g.edgeCount();
        int need = g.nodeCount() - 1;
        double best = Double.MAX_VALUE;
        for (int mask = 0; mask < (1 << m); mask++) {
            if (Integer.bitCount(mask) != need) {
                continue;
            }
            int[] chosen = new int[need];
            int k = 0;
            double sum = 0;
            for (int i = 0; i < m; i++) {
                if ((mask >> i & 1) == 1) {
                    chosen[k++] = i;
                    sum += g.edges().get(i).weight();
                }
            }
            if (connected(g, chosen)) {
                best = Math.min(best, sum);
            }
        }
        return best;
    }

    private static double bellman(Preset p) {
        Graph g = p.graph();
        double[] d = new double[g.nodeCount()];
        Arrays.fill(d, Double.MAX_VALUE);
        d[p.source()] = 0;
        for (int round = 0; round < g.nodeCount(); round++) {
            for (Edge e : g.edges()) {
                if (d[e.from()] != Double.MAX_VALUE && d[e.from()] + e.weight() < d[e.to()]) {
                    d[e.to()] = d[e.from()] + e.weight();
                }
                if (!g.isDirected() && d[e.to()] != Double.MAX_VALUE && d[e.to()] + e.weight() < d[e.from()]) {
                    d[e.from()] = d[e.to()] + e.weight();
                }
            }
        }
        return d[p.target()];
    }

    @Test
    public void bundledBankLoadsBothPresets() {
        assertEquals(2, PresetBank.current().presets().size());
        assertEquals(List.of(PresetTool.MAX_FLOW), preset("flood-relief").tools());
        assertEquals(List.of(PresetTool.SHORTEST_ROUTE, PresetTool.MST), preset("city-bus").tools());
    }

    @Test
    public void floodReliefFlowEqualsBruteForceMinCut() {
        Preset p = preset("flood-relief");
        PresetResult r = PresetRunner.run(p, PresetTool.MAX_FLOW);
        assertEquals(bruteMinCut(p), r.value(), EPS);
        double cut = 0;
        for (int id : r.highlightEdges()) {
            cut += p.graph().edge(id).weight();
        }
        assertEquals("the cut edges add up to the flow", r.value(), cut, EPS);
        assertFalse(r.flowEdges().isEmpty());
    }

    @Test
    public void cityBusSpanningTreeEqualsBruteForce() {
        Preset p = preset("city-bus");
        PresetResult r = PresetRunner.run(p, PresetTool.MST);
        assertEquals(bruteMst(p.graph()), r.value(), EPS);
        assertEquals(p.graph().nodeCount() - 1, r.highlightEdges().size());
    }

    @Test
    public void cityBusRouteEqualsBellmanFordAndIsAWalk() {
        Preset p = preset("city-bus");
        PresetResult r = PresetRunner.run(p, PresetTool.SHORTEST_ROUTE);
        assertTrue(r.reachable());
        assertEquals(bellman(p), r.value(), EPS);
        assertEquals(p.source(), (int) r.route().get(0));
        assertEquals(p.target(), (int) r.route().get(r.route().size() - 1));
        double sum = 0;
        for (int id : r.highlightEdges()) {
            sum += p.graph().edge(id).weight();
        }
        assertEquals(r.value(), sum, EPS);
    }

    @Test
    public void everyOfferedToolReplaysAsATrace() {
        for (Preset p : PresetBank.current().presets()) {
            for (PresetTool t : p.tools()) {
                assertTrue(PresetRunner.trace(p, t).size() > 1);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void aToolTheStoryDoesNotOfferIsRefused() {
        PresetRunner.run(preset("flood-relief"), PresetTool.MST);
    }

    private static String base(String tools, String directed, int target) {
        return "{\"schemaVersion\":1,\"presets\":[{\"id\":\"x\",\"title\":\"T\",\"story\":\"S\",\"unit\":\"u\",\"tools\":" + tools
                + ",\"source\":0,\"target\":" + target + ",\"graph\":{\"format\":\"graphlab-graph\",\"version\":1,\"name\":\"g\","
                + "\"directed\":" + directed + ",\"weighted\":true,\"nodes\":[{\"id\":0,\"label\":\"A\",\"x\":0,\"y\":0},"
                + "{\"id\":1,\"label\":\"B\",\"x\":9,\"y\":0},{\"id\":2,\"label\":\"C\",\"x\":9,\"y\":9}],"
                + "\"edges\":[{\"id\":0,\"from\":0,\"to\":1,\"weight\":2}]}}]}";
    }

    private static void rejects(String json, String part) {
        try {
            PresetBank.parse(json);
            fail("should be refused: " + part);
        } catch (ContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(part));
        }
    }

    @Test
    public void damagedPresetsAreRefusedWithAReason() throws ContentException {
        PresetBank.parse(base("[\"shortest-route\"]", "false", 1));
        rejects(base("[\"shortest-route\"]", "false", 2), "no route");
        rejects(base("[\"max-flow\"]", "false", 1), "directed");
        rejects(base("[\"mst\"]", "true", 1), "undirected");
        rejects(base("[\"bogus\"]", "false", 1), "unknown tool");
        rejects(base("[]", "false", 1), "tools");
        rejects(base("[\"max-flow\"]", "true", 2), "no flow");
        rejects(base("[\"shortest-route\"]", "false", 7), "source and target");
        rejects("{\"schemaVersion\":9,\"presets\":[]}", "schemaVersion");
    }
}
