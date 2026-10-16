package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Closest pair of points by divide and conquer (MS18). The nodes are the points; their positions on the canvas are the coordinates
 * and the edges are ignored. The points are sorted by x and split in the middle. The best pair is the best of the left half, the right
 * half, and any pair that crosses the middle: such a pair must lie in the strip of width 2d around the middle line (d is the best
 * distance so far), and in the strip, sorted by y, each point only needs to be compared with the next few (at most 7).
 *
 * <p>Result: {@link Result#value()} is the distance, {@link Result#chosen()} the two node ids (smaller id first). Ties are decided by the
 * first pair found; the distance is always the true smallest one.
 */
public final class ClosestPair implements Algorithm {
    public static final String ID = "closest-pair";
    private static final int MAX_STEPS = 4000;

    private static final List<String> PSEUDOCODE = List.of(
            "sort the points by x",
            "solve(points): if there are 3 or fewer, compare every pair and return the best",
            "split the points at the middle into a left and a right half",
            "d = the smaller of solve(left) and solve(right)",
            "collect the points whose x is within d of the middle line (the strip), sorted by y",
            "for each point in the strip, compare it with the next points while their y is within d",
            "return the best distance found");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Closest pair of points";
    }

    @Override
    public boolean needsSource() {
        return false;
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.DIVIDE_AND_CONQUER;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    private static final class Best {
        double d = Double.POSITIVE_INFINITY;
        Node a;
        Node b;
    }

    private final class Run {
        final Graph graph;
        final GraphFrame.Builder paint;
        final TraceRecorder rec = new TraceRecorder().counter("distances", 0)
                .limit(MAX_STEPS, "This graph needs too many steps to show. Try one with fewer nodes.");
        final Best best = new Best();
        long distances;

        Run(Graph graph) {
            this.graph = graph;
            this.paint = GraphFrame.builder(graph);
        }

        double dist(Node p, Node q) {
            return Math.hypot(p.x() - q.x(), p.y() - q.y());
        }

        void compare(Node p, Node q, int line) {
            distances++;
            rec.set("distances", distances);
            double d = dist(p, q);
            boolean better = d < best.d;
            if (better) {
                if (best.a != null) {
                    paint.node(best.a.id(), State.VISITED).node(best.b.id(), State.VISITED);
                }
                best.d = d;
                best.a = p;
                best.b = q;
                paint.node(p.id(), State.PATH).node(q.id(), State.PATH);
            }
            rec.step(paint.build(), line, "Distance " + graph.nodeName(p.id()) + "–" + graph.nodeName(q.id()) + " = " + fmt(d)
                    + (better ? ": the best pair so far" : ": not better than " + fmt(best.d)));
        }

        void solve(List<Node> pts, int lo, int hi) {   // pts sorted by x; works on [lo, hi)
            int count = hi - lo;
            if (count <= 3) {
                for (int i = lo; i < hi; i++) {
                    paint.node(pts.get(i).id(), State.FRONTIER);
                }
                rec.step(paint.build(), 2, "Only " + count + (count == 1 ? " point" : " points") + " here: compare every pair");
                for (int i = lo; i < hi; i++) {
                    for (int j = i + 1; j < hi; j++) {
                        compare(pts.get(i), pts.get(j), 2);
                    }
                }
                for (int i = lo; i < hi; i++) {
                    if (paint.nodeState(pts.get(i).id()) == State.FRONTIER) {
                        paint.node(pts.get(i).id(), State.VISITED);
                    }
                }
                return;
            }
            int mid = lo + count / 2;
            double midX = pts.get(mid).x();
            for (int i = lo; i < mid; i++) {
                setIfPlain(pts.get(i), State.FRONTIER);
            }
            rec.step(paint.build(), 3, "Split " + count + " points at x = " + fmt(midX) + ": " + (mid - lo) + " on the left, " + (hi - mid)
                    + " on the right");
            solve(pts, lo, mid);
            solve(pts, mid, hi);
            double d = best.d;
            List<Node> strip = new ArrayList<>();
            for (int i = lo; i < hi; i++) {
                if (Math.abs(pts.get(i).x() - midX) < d) {
                    strip.add(pts.get(i));
                }
            }
            strip.sort(Comparator.comparingDouble(Node::y).thenComparingInt(Node::id));
            for (Node p : strip) {
                setIfPlain(p, State.CHOSEN);
            }
            rec.step(paint.build(), 5, "Strip around x = " + fmt(midX) + " (width " + fmt(2 * d) + "): " + strip.size()
                    + (strip.size() == 1 ? " point" : " points") + " to check against each other");
            for (int i = 0; i < strip.size(); i++) {
                for (int j = i + 1; j < strip.size() && strip.get(j).y() - strip.get(i).y() < best.d; j++) {
                    compare(strip.get(i), strip.get(j), 6);
                }
            }
            for (Node p : strip) {
                if (paint.nodeState(p.id()) == State.CHOSEN) {
                    paint.node(p.id(), State.VISITED);
                }
            }
            for (int i = lo; i < hi; i++) {
                setIfPlain(pts.get(i), State.VISITED);
            }
        }

        void setIfPlain(Node p, State state) {
            if (paint.nodeState(p.id()) != State.PATH) {
                paint.node(p.id(), state);
            }
        }
    }

    static String fmt(double d) {
        return com.graphlab.engine.trace.Labels.number(Math.round(d * 100) / 100.0);
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        if (graph.nodeCount() < 2) {
            throw new IllegalArgumentException(name() + " needs at least two nodes.");
        }
        Run run = new Run(graph);
        List<Node> pts = new ArrayList<>(graph.nodes());
        pts.sort(Comparator.comparingDouble(Node::x).thenComparingDouble(Node::y).thenComparingInt(Node::id));
        run.rec.step(run.paint.build(), 1, "Sort the " + pts.size() + " points by x (the node positions are the coordinates)");
        run.solve(pts, 0, pts.size());
        Node a = run.best.a;
        Node b = run.best.b;
        for (Node p : graph.nodes()) {
            run.paint.node(p.id(), State.IDLE);
        }
        run.paint.node(a.id(), State.PATH).node(b.id(), State.PATH).label(a.id(), "closest").label(b.id(), "closest");
        List<Integer> chosen = new ArrayList<>(List.of(Math.min(a.id(), b.id()), Math.max(a.id(), b.id())));
        run.rec.step(run.paint.build(), 7, "The closest pair is " + graph.nodeName(chosen.get(0)) + " and " + graph.nodeName(chosen.get(1))
                + ", at distance " + fmt(run.best.d) + " (" + run.distances + " distances computed)");
        Result result = Result.builder().value(run.best.d).chosen(chosen).build();
        return new Trace(ID, params, run.rec.steps(), result);
    }
}
