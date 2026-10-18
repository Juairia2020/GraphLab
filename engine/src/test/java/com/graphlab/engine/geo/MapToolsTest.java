package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class MapToolsTest {
    private static final double EPS = 1e-6;

    private static List<City> cities(String... names) {
        List<City> out = new ArrayList<>();
        for (String n : names) {
            out.add(Cities.byName(n));
        }
        return out;
    }

    private static RouteMatrix scaled(List<City> places, double detour) {
        int n = places.size();
        double[][] km = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                km[i][j] = i == j ? 0 : Geo.haversineKm(places.get(i), places.get(j)) * detour;
            }
        }
        return new RouteMatrix(places, km, "roads");
    }

    private static double[][] floyd(Graph g) {
        int n = g.nodeCount();
        double[][] d = new double[n][n];
        for (double[] row : d) {
            java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int i = 0; i < n; i++) {
            d[i][i] = 0;
        }
        for (var e : g.edges()) {
            d[e.from()][e.to()] = Math.min(d[e.from()][e.to()], e.weight());
            d[e.to()][e.from()] = Math.min(d[e.to()][e.from()], e.weight());
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
                }
            }
        }
        return d;
    }

    @Test
    public void khulnaToSylhetRouteEqualsAHandCheckOnTheSameMatrix() {
        List<City> places = cities("Khulna", "Dhaka", "Sylhet", "Barishal", "Cumilla", "Rajshahi");
        RouteMatrix m = scaled(places, 1.3);
        MapTools.Route r = MapTools.shortestRoute(m, 0, 2);
        assertTrue(r.reached());
        assertEquals(floyd(r.graph())[0][2], r.km(), EPS);
        assertEquals(0, (int) r.path().get(0));
        assertEquals(2, (int) r.path().get(r.path().size() - 1));
        double sum = 0;
        for (int i = 1; i < r.path().size(); i++) {
            int a = r.path().get(i - 1);
            int b = r.path().get(i);
            sum += r.graph().edges().stream().filter(e -> (e.from() == a && e.to() == b) || (e.from() == b && e.to() == a))
                    .mapToDouble(e -> e.weight()).min().getAsDouble();
        }
        assertEquals("the path's edges add up to the distance", r.km(), sum, EPS);
    }

    @Test
    public void aStarGivesTheSameLengthAsDijkstraAndExploresNoMore() {
        Random random = new Random(7);
        List<City> all = Cities.all();
        for (int trial = 0; trial < 60; trial++) {
            List<City> pool = new ArrayList<>(all);
            java.util.Collections.shuffle(pool, random);
            List<City> places = pool.subList(0, 4 + random.nextInt(7));
            RouteMatrix m = scaled(new ArrayList<>(places), 1.2 + random.nextDouble());
            int from = random.nextInt(places.size());
            int to = (from + 1 + random.nextInt(places.size() - 1)) % places.size();
            MapTools.Route r = MapTools.shortestRoute(m, from, to);
            AStar.Answer a = AStar.run(r.graph(), m.places(), from, to);
            assertTrue(r.heuristicAdmissible());
            assertEquals(r.km(), a.km(), EPS);
            assertTrue(a.expanded().size() <= m.places().size());
        }
    }

    @Test
    public void aStarWarnsWhenAnEdgeIsShorterThanTheStraightLine() {
        List<City> places = cities("Khulna", "Dhaka", "Sylhet");
        RouteMatrix m = scaled(places, 0.5);   // impossible roads: half the straight line
        assertFalse(MapTools.shortestRoute(m, 0, 2).heuristicAdmissible());
    }

    private static double brute(double[][] d, int start) {
        int n = d.length;
        int[] idx = new int[n - 1];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (i != start) {
                idx[k++] = i;
            }
        }
        return permute(d, start, idx, 0);
    }

    private static double permute(double[][] d, int start, int[] a, int at) {
        if (at == a.length) {
            double cost = d[start][a[0]];
            for (int i = 1; i < a.length; i++) {
                cost += d[a[i - 1]][a[i]];
            }
            return cost + d[a[a.length - 1]][start];
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = at; i < a.length; i++) {
            int t = a[at];
            a[at] = a[i];
            a[i] = t;
            best = Math.min(best, permute(d, start, a, at + 1));
            t = a[at];
            a[at] = a[i];
            a[i] = t;
        }
        return best;
    }

    @Test
    public void exactTourEqualsBruteForceOnRandomMatricesIncludingAsymmetricOnes() {
        Random random = new Random(11);
        for (int trial = 0; trial < 80; trial++) {
            int n = 3 + random.nextInt(6);
            double[][] d = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    d[i][j] = i == j ? 0 : 1 + random.nextInt(50);
                }
            }
            int start = random.nextInt(n);
            Tsp.Tour exact = Tsp.exact(d, start);
            Tsp.Tour nn = Tsp.nearestNeighbour(d, start);
            assertEquals(brute(d, start), exact.cost(), EPS);
            assertTrue(nn.cost() >= exact.cost() - EPS);
            assertEquals(n + 1, exact.order().size());
            assertEquals(start, (int) exact.order().get(0));
            assertEquals(start, (int) exact.order().get(n));
            double sum = 0;
            for (int i = 1; i < exact.order().size(); i++) {
                sum += d[exact.order().get(i - 1)][exact.order().get(i)];
            }
            assertEquals(exact.cost(), sum, EPS);
        }
    }

    @Test
    public void pruningVisitsFarFewerStatesThanAllPermutations() {
        List<City> places = cities("Khulna", "Dhaka", "Sylhet", "Barishal", "Cumilla", "Rajshahi", "Rangpur", "Chattogram", "Jashore");
        double[][] d = scaled(places, 1.3).km();
        Tsp.Tour t = Tsp.exact(d, 0);
        long all = 1;
        for (int i = 2; i <= 8; i++) {
            all *= i;   // 8! = 40320 full tours
        }
        assertTrue("visited " + t.nodesVisited(), t.nodesVisited() < all);
    }

    @Test
    public void sixCityRoundTripShowsExactAndHeuristicCost() {
        List<City> places = cities("Khulna", "Dhaka", "Sylhet", "Chattogram", "Rajshahi", "Barishal");
        MapTools.Trip trip = MapTools.roundTrip(scaled(places, 1.3), 0);
        assertTrue(trip.nearest().cost() >= trip.exact().cost() - EPS);
        assertTrue(trip.excess() >= 0);
        assertEquals(7, trip.exact().order().size());
    }

    @Test
    public void kruskalAndPrimAgreeOnTheCheapestNetwork() {
        List<City> places = cities("Khulna", "Dhaka", "Sylhet", "Chattogram", "Rajshahi", "Barishal", "Rangpur");
        MapTools.Network net = MapTools.cheapestNetwork(scaled(places, 1.3));
        assertEquals(net.km(), net.primKm(), EPS);
        assertEquals(places.size() - 1, net.edgeIds().size());
    }

    @Test
    public void theCityGraphIsCompleteAndKeepsTheMapShape() {
        List<City> places = cities("Khulna", "Dhaka", "Sylhet", "Chattogram");
        Graph g = CityGraph.build(scaled(places, 1.3));
        assertEquals(4, g.nodeCount());
        assertEquals(6, g.edgeCount());
        assertTrue("Sylhet is north-east of Khulna: larger x, smaller y",
                g.node(2).x() > g.node(0).x() && g.node(2).y() < g.node(0).y());
    }

    @Test
    public void theLabGraphIsSparseButKeepsDistancesAndTheNetwork() {
        List<City> places = cities("Khulna", "Dhaka", "Sylhet", "Chattogram", "Rajshahi", "Barishal", "Rangpur", "Cumilla", "Jashore", "Bogura");
        Graph full = CityGraph.build(scaled(places, 1.3));
        Graph lab = CityGraph.forLab(full);
        assertTrue(lab.edgeCount() < full.edgeCount());
        assertTrue(lab.edgeCount() >= places.size() - 1);
        double a = com.graphlab.engine.algo.Algorithms.byId("dijkstra").run(full, new com.graphlab.engine.trace.Params(0, 0L, 9)).result().distances().get(9);
        double b = com.graphlab.engine.algo.Algorithms.byId("dijkstra").run(lab, new com.graphlab.engine.trace.Params(0, 0L, 9)).result().distances().get(9);
        assertEquals(a, b, EPS);
        double kf = com.graphlab.engine.algo.Algorithms.byId("kruskal").run(full, com.graphlab.engine.trace.Params.from(0)).result().totalWeight();
        double kl = com.graphlab.engine.algo.Algorithms.byId("kruskal").run(lab, com.graphlab.engine.trace.Params.from(0)).result().totalWeight();
        assertEquals(kf, kl, EPS);
        for (int i = 0; i < lab.nodeCount(); i++) {
            for (int j = i + 1; j < lab.nodeCount(); j++) {
                assertTrue(Math.hypot(lab.node(i).x() - lab.node(j).x(), lab.node(i).y() - lab.node(j).y()) >= CityGraph.LAB_MIN_GAP - 1e-6);
            }
        }
    }
}
