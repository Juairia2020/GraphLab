package com.graphlab.engine.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Round trips. {@link #exact} is depth-first branch and bound: a partial tour is dropped as soon as it costs at least as
 * much as the best full tour found so far (pruning), which is what keeps ten places fast. {@link #nearestNeighbour} always
 * drives to the closest unvisited place and gives a quick, not always best, tour. Tours start and end at {@code start}.
 */
public final class Tsp {
    public record Tour(List<Integer> order, double cost, long nodesVisited) {
        public Tour {
            order = List.copyOf(order);
        }
    }

    private Tsp() {
    }

    public static Tour nearestNeighbour(double[][] d, int start) {
        int n = d.length;
        boolean[] seen = new boolean[n];
        List<Integer> order = new ArrayList<>();
        order.add(start);
        seen[start] = true;
        double cost = 0;
        int at = start;
        for (int step = 1; step < n; step++) {
            int best = -1;
            for (int v = 0; v < n; v++) {
                if (!seen[v] && (best < 0 || d[at][v] < d[at][best])) {
                    best = v;
                }
            }
            seen[best] = true;
            order.add(best);
            cost += d[at][best];
            at = best;
        }
        cost += d[at][start];
        order.add(start);
        return new Tour(order, cost, n);
    }

    public static Tour exact(double[][] d, int start) {
        int n = d.length;
        Tour seed = nearestNeighbour(d, start);
        State s = new State(d, start, seed.cost(), new ArrayList<>(seed.order()));
        s.path.add(start);
        s.seen[start] = true;
        s.search(start, 0, 1);
        return new Tour(s.best, s.bestCost, s.visited);
    }

    private static final class State {
        final double[][] d;
        final int start;
        final boolean[] seen;
        final List<Integer> path = new ArrayList<>();
        List<Integer> best;
        double bestCost;
        long visited;

        State(double[][] d, int start, double bound, List<Integer> seedOrder) {
            this.d = d;
            this.start = start;
            this.seen = new boolean[d.length];
            this.bestCost = bound;
            this.best = seedOrder;
        }

        void search(int at, double cost, int count) {
            visited++;
            if (count == d.length) {
                double total = cost + d[at][start];
                if (total < bestCost - 1e-12) {
                    bestCost = total;
                    best = new ArrayList<>(path);
                    best.add(start);
                }
                return;
            }
            for (int v = 0; v < d.length; v++) {
                if (seen[v]) {
                    continue;
                }
                double next = cost + d[at][v];
                if (next >= bestCost - 1e-12) {
                    continue;   // pruned: already no better than the best full tour
                }
                seen[v] = true;
                path.add(v);
                search(v, next, count + 1);
                path.remove(path.size() - 1);
                seen[v] = false;
            }
        }
    }
}
