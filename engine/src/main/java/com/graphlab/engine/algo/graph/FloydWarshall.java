package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import com.graphlab.engine.trace.Visual;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Floyd-Warshall: the shortest distance between every pair of nodes. The table starts with the
 * edge weights, then each node in turn is allowed as a stop on the way: a distance is replaced when
 * going through that node is shorter. Works with negative weights; a negative cycle shows up as a
 * negative number on the diagonal.
 *
 * <p>The table: row k and column k (the two halves of a path through k) are FRONTIER, the cell that
 * just improved is CHOSEN, and at the end a negative diagonal cell is FAIL. Up to 8 nodes every
 * improvement is its own step; bigger graphs get one step per node allowed.
 */
public final class FloydWarshall implements Algorithm {
    public static final String ID = "floyd-warshall";
    /** Up to this many nodes, every improved cell is a step of its own. */
    static final int FINE_STEPS_UP_TO = 8;

    private static final List<String> PSEUDOCODE = List.of(
            "dist[i][j] = weight of the edge i to j; 0 if i = j; infinity if there is none",
            "for each node k  (allow k as a stop on the way)",
            "    for each pair (i, j)",
            "        if dist[i][k] + dist[k][j] < dist[i][j]",
            "            dist[i][j] = dist[i][k] + dist[k][j]",
            "if some dist[i][i] < 0: there is a negative cycle",
            "dist[i][j] is now the shortest distance from i to j");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Floyd-Warshall";
    }

    @Override
    public boolean needsSource() {
        return false;
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.SHORTEST_PATHS;
    }

    @Override
    public Visual visual() {
        return Visual.GRID;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        int n = graph.nodeCount();
        Map<Integer, Integer> index = new HashMap<>();
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Node node = graph.nodes().get(i);
            index.put(node.id(), i);
            headers.add(node.label());
        }

        double[][] d = new double[n][n];
        for (double[] row : d) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int i = 0; i < n; i++) {
            d[i][i] = 0;
        }
        for (Edge edge : graph.edges()) {
            int u = index.get(edge.from());
            int v = index.get(edge.to());
            d[u][v] = Math.min(d[u][v], edge.weight());
            if (!graph.isDirected()) {
                d[v][u] = Math.min(d[v][u], edge.weight());
            }
        }

        GridFrame.Builder paint = GridFrame.builder("from \\ to", headers, headers);
        TraceRecorder rec = new TraceRecorder().counter("stops allowed", 0).counter("improved", 0);
        fill(paint, d);
        rec.step(paint.build(), 1, "Start: the distance from one node to another is the weight of the edge between them, 0 to itself, and "
                + "\u221e where there is no edge");

        boolean fine = n <= FINE_STEPS_UP_TO;
        long improved = 0;
        for (int k = 0; k < n; k++) {
            rec.set("stops allowed", k + 1);
            paint.recolour(State.CHOSEN, State.IDLE).recolour(State.FRONTIER, State.IDLE);
            paint.stateRow(k, State.FRONTIER).stateCol(k, State.FRONTIER);
            List<int[]> changed = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            // The k-th step is recorded first in fine mode so the person sees which row and column are used.
            if (fine) {
                rec.step(paint.build(), 2, "Allow " + headers.get(k) + " as a stop on the way: look for pairs that get shorter through it");
            }
            long thisRound = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (Double.isInfinite(d[i][k]) || Double.isInfinite(d[k][j])) {
                        continue;
                    }
                    double through = d[i][k] + d[k][j];
                    if (through < d[i][j]) {
                        double old = d[i][j];
                        d[i][j] = through;
                        improved++;
                        thisRound++;
                        rec.set("improved", improved);
                        paint.set(i, j, text(through));
                        if (fine) {
                            paint.recolour(State.CHOSEN, State.IDLE);
                            paint.stateRow(k, State.FRONTIER).stateCol(k, State.FRONTIER);
                            paint.state(i, j, State.CHOSEN);
                            rec.step(paint.build(), 5, headers.get(i) + " to " + headers.get(j) + " through " + headers.get(k)
                                    + ": " + text(d[i][k]) + " + " + text(d[k][j]) + " = " + text(through)
                                    + (Double.isInfinite(old) ? ", better than no path" : ", shorter than " + text(old)));
                        } else {
                            changed.add(new int[] {i, j});
                            notes.add(headers.get(i) + "\u2192" + headers.get(j));
                        }
                    }
                }
            }
            if (!fine) {
                paint.recolour(State.CHOSEN, State.IDLE);
                paint.stateRow(k, State.FRONTIER).stateCol(k, State.FRONTIER);
                for (int[] cell : changed) {
                    paint.state(cell[0], cell[1], State.CHOSEN);
                }
                rec.step(paint.build(), 5, "Allow " + headers.get(k) + " as a stop: " + thisRound
                        + (thisRound == 1 ? " distance gets" : " distances get") + " shorter");
            } else if (thisRound == 0) {
                rec.step(paint.build(), 4, "No distance gets shorter through " + headers.get(k));
            }
        }

        paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED).recolour(State.IDLE, State.VISITED);
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (d[i][i] < 0) {
                bad.add(headers.get(i));
                paint.state(i, i, State.FAIL);
            }
        }
        Result.Builder result = Result.builder();
        List<List<Double>> rows = new ArrayList<>();
        for (double[] row : d) {
            List<Double> r = new ArrayList<>();
            for (double v : row) {
                r.add(v);
            }
            rows.add(r);
        }
        result.matrix(rows);
        if (!bad.isEmpty()) {
            result.negativeCycle(true);
            rec.step(paint.build(), 6, "Negative cycle: " + String.join(", ", bad)
                    + (bad.size() == 1 ? " has" : " have") + " a negative distance to itself, so going round it again and again keeps making paths shorter");
        } else {
            rec.step(paint.build(), 7, "Done: row i, column j is the shortest distance from i to j (\u221e means no path)");
        }
        return new Trace(ID, params, rec.steps(), result.build());
    }

    private static void fill(GridFrame.Builder paint, double[][] d) {
        for (int i = 0; i < d.length; i++) {
            for (int j = 0; j < d.length; j++) {
                paint.set(i, j, text(d[i][j]));
            }
        }
    }

    private static String text(double v) {
        return Double.isInfinite(v) ? (v > 0 ? "\u221e" : "-\u221e") : Labels.number(v);
    }
}
