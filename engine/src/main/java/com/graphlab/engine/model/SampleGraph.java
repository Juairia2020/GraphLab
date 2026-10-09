package com.graphlab.engine.model;

/**
 * The 6-node sample graph used in demos and tests (and as the Lab's first preset).
 *
 * <pre>
 *  A-B 7   A-C 9   A-F 14  B-C 10  B-D 15  C-D 11  C-F 2   D-E 6   E-F 9
 * </pre>
 *
 * Shortest distances from A: A 0, B 7, C 9, F 11, D 20, E 20. Minimum spanning tree weight: 33.
 * Edge ids 0 to 8 follow the order above.
 */
public final class SampleGraph {
    public static final int A = 0;
    public static final int B = 1;
    public static final int C = 2;
    public static final int D = 3;
    public static final int E = 4;
    public static final int F = 5;

    private SampleGraph() {
    }

    public static Graph sixNodeUndirected() {
        return Graph.builder(false)
                .node(A, 10, 50, "A")
                .node(B, 30, 15, "B")
                .node(C, 40, 60, "C")
                .node(D, 70, 15, "D")
                .node(E, 90, 50, "E")
                .node(F, 65, 80, "F")
                .edge(0, A, B, 7)
                .edge(1, A, C, 9)
                .edge(2, A, F, 14)
                .edge(3, B, C, 10)
                .edge(4, B, D, 15)
                .edge(5, C, D, 11)
                .edge(6, C, F, 2)
                .edge(7, D, E, 6)
                .edge(8, E, F, 9)
                .build();
    }
}
