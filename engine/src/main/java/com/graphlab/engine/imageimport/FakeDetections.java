package com.graphlab.engine.imageimport;

import java.util.List;

/**
 * Made-up detection results with a few doubtful items. The review screen was built against these first, so the whole
 * flow could be tried before any detector existed; it also serves as the demo ("Try the review screen").
 */
public final class FakeDetections {
    private FakeDetections() {
    }

    public static DetectedGraph demo() {
        List<DetectedGraph.Node> nodes = List.of(
                new DetectedGraph.Node(0, "A", 100, 100, 0.97, false, ""),
                new DetectedGraph.Node(1, "B", 300, 80, 0.95, false, ""),
                new DetectedGraph.Node(2, "C", 480, 140, 0.93, false, ""),
                new DetectedGraph.Node(3, "D", 160, 300, 0.55, false, "the name was hard to read"),
                new DetectedGraph.Node(4, "E", 360, 320, 0.96, true, ""),
                new DetectedGraph.Node(5, "F", 520, 330, 0.94, false, ""));
        List<DetectedGraph.Edge> edges = List.of(
                new DetectedGraph.Edge(0, 0, 1, 4.0, 0.95, 0.92, ""),
                new DetectedGraph.Edge(1, 1, 2, 7.0, 0.93, 0.90, ""),
                new DetectedGraph.Edge(2, 0, 3, 2.0, 0.90, 0.48, "read \"Z\" as 2"),
                new DetectedGraph.Edge(3, 1, 4, 5.0, 0.52, 0.88, "the line does not quite touch the circle"),
                new DetectedGraph.Edge(4, 3, 4, null, 0.94, 0.30, "no weight was found near this line"),
                new DetectedGraph.Edge(5, 4, 5, 3.0, 0.92, 0.91, ""),
                new DetectedGraph.Edge(6, 2, 5, 6.0, 0.91, 0.55, "the number could belong to two lines"));
        return new DetectedGraph(nodes, edges, false, 1.0, true, true, DetectedGraph.Source.DEMO,
                List.of("The number \"9\" near (410, 210) was not matched to any line."));
    }
}
