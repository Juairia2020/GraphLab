package com.graphlab.engine.trace;

import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import org.junit.Test;

public class TraceFormatterTest {

    @Test
    public void printsDijkstraTraceOnSampleGraph() {
        Graph graph = SampleGraph.sixNodeUndirected();
        Algorithm dijkstra = Algorithms.byId("dijkstra");
        Trace trace = dijkstra.run(graph, Params.from(SampleGraph.A));
        String text = TraceFormatter.format(graph, dijkstra, trace);

        System.out.println(text);   // visible in the Gradle test output: this is the MS1 demo

        assertTrue(text.startsWith("Dijkstra"));
        assertTrue(text.contains("Settle A"));
        assertTrue(text.contains("distances   : A=0 B=7 C=9 D=20 E=20 F=11"));
        assertTrue(text.contains("total weight 38"));
    }

    @Test
    public void everyStepAppearsInTheOutput() {
        Graph graph = SampleGraph.sixNodeUndirected();
        Algorithm bfs = Algorithms.byId("bfs");
        Trace trace = bfs.run(graph, Params.from(SampleGraph.A));
        String text = TraceFormatter.format(graph, bfs, trace);
        for (Step step : trace.steps()) {
            assertTrue(text.contains(step.message()));
        }
    }
}
