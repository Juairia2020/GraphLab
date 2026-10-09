package com.graphlab.engine.demo;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceFormatter;

/**
 * Prints a trace on the 6-node sample graph. Usage: {@code TraceDemo [algorithm-id] [start-node-id]},
 * for example {@code TraceDemo dijkstra 0}. Defaults: dijkstra, node 0 (A).
 */
public final class TraceDemo {
    private TraceDemo() {
    }

    public static void main(String[] args) {
        String id = args.length > 0 ? args[0] : "dijkstra";
        int source = args.length > 1 ? Integer.parseInt(args[1]) : SampleGraph.A;
        Graph graph = SampleGraph.sixNodeUndirected();
        Algorithm algorithm = Algorithms.byId(id);
        Trace trace = algorithm.run(graph, Params.from(source));
        System.out.print(TraceFormatter.format(graph, algorithm, trace));
    }
}
