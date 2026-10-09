package com.graphlab.engine.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class StepModelTest {

    private final Graph graph = SampleGraph.sixNodeUndirected();

    // ---- GraphFrame ----

    @Test
    public void initialFrameIsAllIdleAndUnlabelled() {
        GraphFrame frame = GraphFrame.initial(graph);
        assertEquals(6, frame.nodeStates().size());
        assertEquals(9, frame.edgeStates().size());
        assertTrue(frame.nodeStates().values().stream().allMatch(s -> s == State.IDLE));
        assertTrue(frame.edgeStates().values().stream().allMatch(s -> s == State.IDLE));
        assertEquals("", frame.nodeLabel(0));
    }

    @Test
    public void frameMapsCannotBeChanged() {
        GraphFrame frame = GraphFrame.initial(graph);
        assertThrows(UnsupportedOperationException.class,
                () -> frame.nodeStates().put(0, State.FAIL));
        assertThrows(UnsupportedOperationException.class,
                () -> frame.edgeStates().put(0, State.FAIL));
        assertThrows(UnsupportedOperationException.class,
                () -> frame.nodeLabels().put(0, "x"));
    }

    @Test
    public void snapshotIsNotAffectedByLaterPainting() {
        GraphFrame.Builder paint = GraphFrame.builder(graph);
        paint.node(0, State.CHOSEN).label(0, "7");
        GraphFrame snapshot = paint.build();
        paint.node(0, State.VISITED).label(0, "99").edge(1, State.PATH);
        assertEquals(State.CHOSEN, snapshot.nodeState(0));
        assertEquals("7", snapshot.nodeLabel(0));
        assertEquals(State.IDLE, snapshot.edgeState(1));
    }

    @Test
    public void builderRejectsUnknownIds() {
        GraphFrame.Builder paint = GraphFrame.builder(graph);
        assertThrows(IllegalArgumentException.class, () -> paint.node(99, State.PATH));
        assertThrows(IllegalArgumentException.class, () -> paint.edge(99, State.PATH));
        assertThrows(IllegalArgumentException.class, () -> paint.label(99, "x"));
    }

    @Test
    public void recolouringChangesOnlyMatchingStates() {
        GraphFrame.Builder paint = GraphFrame.builder(graph);
        paint.edge(0, State.FRONTIER).edge(1, State.PATH).node(0, State.FRONTIER).node(1, State.PATH);
        paint.recolourEdges(State.FRONTIER, State.IDLE).recolourNodes(State.FRONTIER, State.VISITED);
        GraphFrame frame = paint.build();
        assertEquals(State.IDLE, frame.edgeState(0));
        assertEquals(State.PATH, frame.edgeState(1));
        assertEquals(State.VISITED, frame.nodeState(0));
        assertEquals(State.PATH, frame.nodeState(1));
    }

    // ---- ArrayFrame ----

    @Test
    public void arrayFrameRejectsLengthMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrayFrame(List.of(1, 2, 3), List.of(State.IDLE)));
    }

    @Test
    public void arrayFrameCopiesItsInput() {
        List<Integer> values = new ArrayList<>(List.of(3, 1, 2));
        List<State> states = new ArrayList<>(List.of(State.IDLE, State.IDLE, State.IDLE));
        ArrayFrame frame = new ArrayFrame(values, states);
        values.set(0, 99);
        assertEquals(List.of(3, 1, 2), frame.values());
        assertThrows(UnsupportedOperationException.class, () -> frame.values().add(1));
    }

    // ---- Step, Trace, recorder ----

    @Test
    public void stepRejectsLineBelowOne() {
        Frame frame = GraphFrame.initial(graph);
        assertThrows(IllegalArgumentException.class, () -> new Step(frame, 0, "x", Map.of()));
    }

    @Test
    public void stepCopiesItsCounters() {
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("a", 1L);
        Step step = new Step(GraphFrame.initial(graph), 1, "x", counters);
        counters.put("a", 50L);
        assertEquals(1L, (long) step.counters().get("a"));
        assertThrows(UnsupportedOperationException.class, () -> step.counters().put("b", 2L));
    }

    @Test
    public void traceNeedsAtLeastOneStep() {
        assertThrows(IllegalArgumentException.class,
                () -> new Trace("x", Params.none(), List.of(), Result.builder().build()));
    }

    @Test
    public void traceReportsSizeAndLastStep() {
        TraceRecorder rec = new TraceRecorder();
        rec.step(GraphFrame.initial(graph), 1, "first");
        rec.step(GraphFrame.initial(graph), 2, "second");
        Trace trace = new Trace("x", Params.none(), rec.steps(), Result.builder().build());
        assertEquals(2, trace.size());
        assertEquals("second", trace.last().message());
        assertEquals("first", trace.step(0).message());
    }

    @Test
    public void recorderSnapshotsCountersPerStep() {
        TraceRecorder rec = new TraceRecorder().counter("n", 0);
        rec.step(GraphFrame.initial(graph), 1, "a");
        rec.increment("n");
        rec.increment("n");
        rec.step(GraphFrame.initial(graph), 1, "b");
        assertEquals(0L, (long) rec.steps().get(0).counters().get("n"));
        assertEquals(2L, (long) rec.steps().get(1).counters().get("n"));
    }

    @Test
    public void recorderRejectsUndeclaredCounter() {
        TraceRecorder rec = new TraceRecorder();
        assertThrows(IllegalArgumentException.class, () -> rec.set("missing", 1));
    }

    // ---- Params, Labels, Result ----

    @Test
    public void paramsHelpers() {
        assertFalse(Params.none().hasSource());
        assertTrue(Params.from(3).hasSource());
        assertEquals(3, Params.from(3).source());
        assertEquals(42L, Params.from(3).withSeed(42).seed());
        assertEquals(3, Params.from(3).withSeed(42).source());
    }

    @Test
    public void labelsFormatNumbers() {
        assertEquals("7", Labels.number(7));
        assertEquals("-3", Labels.number(-3));
        assertEquals("2.5", Labels.number(2.5));
        assertEquals("3.14", Labels.number(3.14159));
        assertEquals("0", Labels.number(0.001));
        assertEquals("0", Labels.number(-0.001));
        assertEquals(Labels.INFINITY, Labels.number(Double.POSITIVE_INFINITY));
        assertEquals("-" + Labels.INFINITY, Labels.number(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void labelsParseReversesNumber() {
        for (double d : new double[] {0, 7, -3, 2.5, 3.14, Double.POSITIVE_INFINITY}) {
            assertEquals(d, Labels.parse(Labels.number(d)), 1e-9);
        }
    }

    @Test
    public void resultPathFollowsParentEdgesRootFirst() {
        // A-B (edge 0), B-C (edge 1): tree rooted at A
        Graph g = Graph.builder(false).nodes(4).edge(0, 1, 1).edge(1, 2, 1).build();
        Result r = Result.builder().visit(0).visit(1).visit(2).parent(1, 0).parent(2, 1).build();
        assertEquals(List.of(0, 1, 2), r.pathTo(g, 2));
        assertEquals(List.of(0), r.pathTo(g, 0));
    }

    @Test
    public void resultPathIsEmptyForUnreachedNode() {
        Graph g = Graph.builder(false).nodes(4).edge(0, 1, 1).build();
        Result r = Result.builder().visit(0).visit(1).parent(1, 0).build();
        assertTrue(r.pathTo(g, 3).isEmpty());
    }

    @Test
    public void resultListsCannotBeChanged() {
        Result r = Result.builder().visit(0).treeEdge(0).build();
        assertThrows(UnsupportedOperationException.class, () -> r.visitOrder().add(1));
        assertThrows(UnsupportedOperationException.class, () -> r.treeEdgeIds().add(1));
        assertSame(r.visitOrder(), r.visitOrder());
    }
}
