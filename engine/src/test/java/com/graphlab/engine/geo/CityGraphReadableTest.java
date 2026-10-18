package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Params;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** The graph that opens in the Lab is sparse and spread out, yet gives the same answers as the complete graph. */
public class CityGraphReadableTest {
    private static List<City> pick(Random r, int n) {
        List<City> all = new ArrayList<>(Cities.all());
        Collections.shuffle(all, r);
        return new ArrayList<>(all.subList(0, n));
    }

    private static boolean connected(Graph g) {
        boolean[] seen = new boolean[g.nodeCount()];
        List<Integer> stack = new ArrayList<>(List.of(0));
        seen[0] = true;
        int count = 1;
        while (!stack.isEmpty()) {
            int u = stack.remove(stack.size() - 1);
            for (Edge e : g.edges()) {
                int v = e.from() == u ? e.to() : e.to() == u ? e.from() : -1;
                if (v >= 0 && !seen[v]) {
                    seen[v] = true;
                    count++;
                    stack.add(v);
                }
            }
        }
        return count == g.nodeCount();
    }

    @Test
    public void sparseSpreadAndSameAnswers() {
        Random r = new Random(11);
        for (int round = 0; round < 120; round++) {
            int n = 3 + r.nextInt(8);
            RouteMatrix m = RouteService.estimate(pick(r, n));
            MapTools.Route route = MapTools.shortestRoute(m, 0, n - 1);
            List<int[]> keep = new ArrayList<>();
            for (int i = 1; i < route.path().size(); i++) {
                keep.add(new int[] {route.path().get(i - 1), route.path().get(i)});
            }
            Graph complete = CityGraph.build(m);
            Graph readable = CityGraph.readable(m, keep);
            assertTrue("connected", connected(readable));
            assertTrue("fewer edges when there are many places", n < 7 || readable.edgeCount() < complete.edgeCount());
            for (int[] k : keep) {
                boolean present = readable.edges().stream().anyMatch(e ->
                        (e.from() == k[0] && e.to() == k[1]) || (e.from() == k[1] && e.to() == k[0]));
                assertTrue("answer link kept", present);
            }
            // nobody sits on top of anybody
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double d = Math.hypot(readable.node(i).x() - readable.node(j).x(), readable.node(i).y() - readable.node(j).y());
                    assertTrue("gap " + d, d >= CityGraph.MIN_GAP - 1e-6);
                }
            }
            // same cheapest network and same shortest route length
            double mstComplete = Algorithms.byId("kruskal").run(complete, Params.from(0)).result().totalWeight();
            double mstReadable = Algorithms.byId("kruskal").run(readable, Params.from(0)).result().totalWeight();
            assertEquals(mstComplete, mstReadable, 1e-6);
            double viaReadable = Algorithms.byId("dijkstra").run(readable, new Params(0, 0L, n - 1)).result().distances().get(n - 1);
            assertEquals(route.km(), viaReadable, 1e-6);
        }
    }
}
