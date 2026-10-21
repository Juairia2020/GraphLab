package com.graphlab.engine.dynamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Written before the implementation: the incremental MST must equal a full Kruskal recompute after every event. */
public class DynamicMstFuzzTest {
    private static final double EPS = 1e-9;

    /** Plain Kruskal on the live edges, the reference answer: total weight and component count. */
    static double[] kruskal(int n, List<DynamicGraph.Edge> live) {
        List<DynamicGraph.Edge> sorted = new ArrayList<>(live);
        sorted.sort((a, b) -> Double.compare(a.weight(), b.weight()));
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        double total = 0;
        int comps = n;
        for (DynamicGraph.Edge e : sorted) {
            int a = find(p, e.u());
            int b = find(p, e.v());
            if (a != b) {
                p[a] = b;
                total += e.weight();
                comps--;
            }
        }
        return new double[] {total, comps};
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) {
            p[x] = p[p[x]];
            x = p[x];
        }
        return x;
    }

    @Test
    public void incrementalMstEqualsFullRecomputeInOneThousandRandomRuns() {
        for (int seed = 0; seed < 1000; seed++) {
            Random random = new Random(seed);
            int n = 2 + random.nextInt(9);
            DynamicGraph g = new DynamicGraph(n);
            List<Integer> alive = new ArrayList<>();
            int steps = 5 + random.nextInt(40);
            for (int step = 0; step < steps; step++) {
                int kind = random.nextInt(10);
                if (kind < 6 || alive.isEmpty()) {
                    int u = random.nextInt(n);
                    int v = random.nextInt(n);
                    if (u == v) {
                        continue;
                    }
                    // few distinct weights on purpose: ties are where an update goes wrong
                    alive.add(g.addEdge(u, v, 1 + random.nextInt(seed % 2 == 0 ? 4 : 30)));
                } else if (kind < 8) {
                    g.removeEdge(alive.remove(random.nextInt(alive.size())));
                } else {
                    g.setWeight(alive.get(random.nextInt(alive.size())), 1 + random.nextInt(30));
                }
                double[] want = kruskal(n, g.liveEdges());
                assertEquals("seed " + seed + " step " + step + ": MST weight", want[0], g.mstWeight(), EPS);
                assertEquals("seed " + seed + " step " + step + ": components", (int) want[1], g.components());
                assertTrue("seed " + seed + ": the tree is a forest of live edges", g.isValidForest());
            }
        }
    }
}
