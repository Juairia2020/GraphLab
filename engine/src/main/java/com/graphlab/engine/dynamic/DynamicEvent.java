package com.graphlab.engine.dynamic;

import java.util.Objects;

/**
 * One change at time {@code t} (the row stored in the events table: t, type, payload). Payloads are plain text:
 * ADD_EDGE "u v weight", REMOVE_EDGE "edgeId", SET_WEIGHT "edgeId weight". Edge ids are given in the order edges are added,
 * starting at 0, so replaying the same events always gives the same ids.
 */
public record DynamicEvent(int t, Type type, String payload) {
    public enum Type { ADD_EDGE, REMOVE_EDGE, SET_WEIGHT }

    public DynamicEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (t < 0) {
            throw new IllegalArgumentException("t must be 0 or more");
        }
    }

    public static DynamicEvent add(int t, int u, int v, double weight) {
        return new DynamicEvent(t, Type.ADD_EDGE, u + " " + v + " " + num(weight));
    }

    public static DynamicEvent remove(int t, int edgeId) {
        return new DynamicEvent(t, Type.REMOVE_EDGE, String.valueOf(edgeId));
    }

    public static DynamicEvent setWeight(int t, int edgeId, double weight) {
        return new DynamicEvent(t, Type.SET_WEIGHT, edgeId + " " + num(weight));
    }

    private static String num(double w) {
        return w == Math.rint(w) ? String.valueOf((long) w) : String.valueOf(w);
    }
}
