package com.graphlab.engine.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import org.junit.Test;

public class FrameSnapshotTest {
    private final Graph graph = SampleGraph.sixNodeUndirected();

    private int indexOf(int nodeId) {
        for (int i = 0; i < graph.nodes().size(); i++) {
            if (graph.nodes().get(i).id() == nodeId) {
                return i;
            }
        }
        throw new AssertionError("no node " + nodeId);
    }

    @Test
    public void followsTheGraphsNodeAndEdgeOrder() {
        GraphFrame frame = GraphFrame.builder(graph)
                .node(SampleGraph.C, State.CHOSEN).label(SampleGraph.C, "9")
                .edge(graph.edge(2).id(), State.PATH).build();
        FrameSnapshot snap = FrameSnapshot.of(graph, frame);
        int cIndex = indexOf(SampleGraph.C);
        assertEquals(State.CHOSEN, snap.nodeState(cIndex));
        assertEquals("9", snap.nodeLabel(cIndex));
        assertEquals(State.PATH, snap.edgeState(2));
        assertEquals(State.IDLE, snap.nodeState(indexOf(SampleGraph.A)));
        assertEquals("", snap.nodeLabel(indexOf(SampleGraph.A)));
        assertEquals(State.IDLE, snap.edgeState(0));
    }

    @Test
    public void fitsOnlyAGraphOfTheSameSize() {
        FrameSnapshot snap = FrameSnapshot.of(graph, GraphFrame.initial(graph));
        assertTrue(snap.fits(6, 9));
        assertFalse(snap.fits(5, 9));
        assertFalse(snap.fits(6, 8));
        assertEquals(6, snap.nodeCount());
        assertEquals(9, snap.edgeCount());
    }

    @Test
    public void missingEntriesDefaultToIdleAndNoLabel() {
        GraphFrame empty = new GraphFrame(java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
        FrameSnapshot snap = FrameSnapshot.of(graph, empty);
        for (int i = 0; i < 6; i++) {
            assertEquals(State.IDLE, snap.nodeState(i));
            assertEquals("", snap.nodeLabel(i));
        }
    }

    /** The Done-when check: stepping to the end and back shows the same frames as the trace, both ways. */
    @Test
    public void everyAlgorithmReplaysIdenticallyForwardAndBackward() {
        for (var algorithm : Algorithms.all()) {
            if (algorithm.visual() != com.graphlab.engine.trace.Visual.GRAPH) {
                continue;   // bars and tables have their own snapshot tests
            }
            Params params = algorithm.needsSource() ? Params.from(SampleGraph.A) : Params.none();
            if (algorithm.needsTarget()) {
                params = params.withTarget(SampleGraph.E);
            }
            Graph graph = algorithm.exampleGraph();
            Trace trace = algorithm.run(graph, params);
            Playback play = new Playback(trace.size());
            FrameSnapshot[] forward = new FrameSnapshot[trace.size()];
            forward[0] = FrameSnapshot.of(graph, (GraphFrame) trace.step(0).frame());
            while (play.next()) {
                forward[play.index()] = FrameSnapshot.of(graph, (GraphFrame) trace.step(play.index()).frame());
            }
            assertEquals(algorithm.id(), trace.size() - 1, play.index());
            while (true) {
                FrameSnapshot back = FrameSnapshot.of(graph, (GraphFrame) trace.step(play.index()).frame());
                assertEquals(algorithm.id() + " step " + play.index(), forward[play.index()], back);
                if (!play.previous()) {
                    break;
                }
            }
            assertEquals(0, play.index());
        }
    }

    @Test
    public void dijkstraLabelsOnTheLastFrameAreTheFinalDistances() {
        Trace trace = Algorithms.byId("dijkstra").run(graph, Params.from(SampleGraph.A));
        FrameSnapshot last = FrameSnapshot.of(graph, (GraphFrame) trace.last().frame());
        for (var node : graph.nodes()) {
            double expected = trace.result().distances().get(node.id());
            assertEquals(node.label(), Labels.number(expected), last.nodeLabel(indexOf(node.id())));
        }
    }

    @Test
    public void differentStepsGiveDifferentSnapshots() {
        Trace trace = Algorithms.byId("dijkstra").run(graph, Params.from(SampleGraph.A));
        FrameSnapshot first = FrameSnapshot.of(graph, (GraphFrame) trace.step(0).frame());
        FrameSnapshot last = FrameSnapshot.of(graph, (GraphFrame) trace.last().frame());
        assertNotEquals(first, last);
        assertNotEquals(first.hashCode(), last.hashCode());
    }
}
