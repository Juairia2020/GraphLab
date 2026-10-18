package com.graphlab.engine.geo;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.Trace;
import java.util.List;

/** The four map tools on a matrix: shortest route, round trip, cheapest network. (Flow uses the presets.) */
public final class MapTools {
    private MapTools() {
    }

    /** Shortest route by Dijkstra (the trace opens in the Lab) and by A*, with how many places each explored. */
    public record Route(Graph graph, Trace trace, List<Integer> path, double km, boolean reached,
                        int dijkstraExplored, int astarExplored, boolean heuristicAdmissible) {
        public Route {
            path = List.copyOf(path);
        }
    }

    public record Trip(Tsp.Tour exact, Tsp.Tour nearest) {
        /** How much longer the quick tour is, as a fraction of the best (0 when equal). */
        public double excess() {
            return exact.cost() <= 0 ? 0 : (nearest.cost() - exact.cost()) / exact.cost();
        }
    }

    public record Network(Graph graph, Trace trace, List<Integer> edgeIds, double km, double primKm) {
        public Network {
            edgeIds = List.copyOf(edgeIds);
        }
    }

    public static Route shortestRoute(RouteMatrix m, int from, int to) {
        Graph g = CityGraph.build(m);
        Trace trace = Algorithms.byId("dijkstra").run(g, new Params(from, 0L, to));
        Result r = trace.result();
        List<Integer> path = r.pathTo(g, to);
        boolean reached = !path.isEmpty();
        AStar.Answer a = AStar.run(g, m.places(), from, to);
        int dijkstraExplored = r.visitOrder().indexOf(to) + 1;
        if (dijkstraExplored <= 0) {
            dijkstraExplored = r.visitOrder().size();
        }
        double km = reached ? r.distances().get(to) : 0;
        return new Route(g, trace, path, km, reached, dijkstraExplored, a.expanded().size(), AStar.admissible(g, m.places()));
    }

    public static Trip roundTrip(RouteMatrix m, int start) {
        double[][] d = m.km();
        return new Trip(Tsp.exact(d, start), Tsp.nearestNeighbour(d, start));
    }

    public static Network cheapestNetwork(RouteMatrix m) {
        Graph g = CityGraph.build(m);
        Trace k = Algorithms.byId("kruskal").run(g, Params.from(0));
        Trace p = Algorithms.byId("prim").run(g, Params.from(0));
        return new Network(g, k, k.result().treeEdgeIds(), k.result().totalWeight(), p.result().totalWeight());
    }
}
