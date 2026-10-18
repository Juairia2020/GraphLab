package com.graphlab.engine.geo;

/**
 * Moves overlapping points apart so that every two are at least {@code minGap} from each other, keeping the picture as
 * close to the map as possible. Used before a map graph is opened in the Lab, where places that are close on the map
 * (two cities 20 km apart) would otherwise sit on top of each other. Deterministic. If pushing does not settle in
 * {@link #ROUNDS} rounds, the points are placed on a circle (always valid).
 */
public final class NodeSpread {
    public static final int ROUNDS = 400;

    private NodeSpread() {
    }

    /** Changes {@code xy} in place; each row is {x, y}. */
    public static void spread(double[][] xy, double minGap) {
        int n = xy.length;
        for (int round = 0; round < ROUNDS; round++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = xy[j][0] - xy[i][0];
                    double dy = xy[j][1] - xy[i][1];
                    double d = Math.hypot(dx, dy);
                    if (d >= minGap - 1e-9) {
                        continue;
                    }
                    if (d < 1e-9) {   // same spot: push along a direction that depends on the pair, so it is repeatable
                        double a = 2 * Math.PI * ((i * 31 + j * 17) % 360) / 360.0;
                        dx = Math.cos(a);
                        dy = Math.sin(a);
                        d = 1;
                    }
                    double push = (minGap - Math.hypot(xy[j][0] - xy[i][0], xy[j][1] - xy[i][1])) / 2 + 0.5;
                    double ux = dx / d;
                    double uy = dy / d;
                    xy[i][0] -= ux * push;
                    xy[i][1] -= uy * push;
                    xy[j][0] += ux * push;
                    xy[j][1] += uy * push;
                    moved = true;
                }
            }
            if (!moved) {
                return;
            }
        }
        if (!settled(xy, minGap)) {
            circle(xy, minGap);
        }
    }

    static boolean settled(double[][] xy, double minGap) {
        for (int i = 0; i < xy.length; i++) {
            for (int j = i + 1; j < xy.length; j++) {
                if (Math.hypot(xy[j][0] - xy[i][0], xy[j][1] - xy[i][1]) < minGap - 1e-6) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void circle(double[][] xy, double minGap) {
        int n = xy.length;
        double r = n < 2 ? 0 : minGap / (2 * Math.sin(Math.PI / n)) * 1.05;
        for (int i = 0; i < n; i++) {
            xy[i][0] = r * Math.cos(2 * Math.PI * i / n);
            xy[i][1] = r * Math.sin(2 * Math.PI * i / n);
        }
    }
}
