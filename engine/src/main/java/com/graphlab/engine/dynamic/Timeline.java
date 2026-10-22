package com.graphlab.engine.dynamic;

import java.util.ArrayList;
import java.util.List;

/**
 * The events of one graph in time order, and the state after the first {@code k} of them (what the timeline slider shows).
 * Replaying from the start each time keeps the slider exact in both directions.
 */
public final class Timeline {
    private final int nodes;
    private final List<DynamicEvent> events;

    public Timeline(int nodes, List<DynamicEvent> events) {
        if (nodes < 1) {
            throw new IllegalArgumentException("A graph needs at least one node");
        }
        this.nodes = nodes;
        List<DynamicEvent> sorted = new ArrayList<>(events);
        sorted.sort((a, b) -> Integer.compare(a.t(), b.t()));   // stable: same t keeps its order
        this.events = List.copyOf(sorted);
        at(this.events.size());                                  // refuse a damaged list now, not on the slider
    }

    public int size() {
        return events.size();
    }

    public List<DynamicEvent> events() {
        return events;
    }

    /** The graph after the first {@code count} events (0 = empty). */
    public DynamicGraph at(int count) {
        if (count < 0 || count > events.size()) {
            throw new IllegalArgumentException("count must be 0.." + events.size());
        }
        DynamicGraph g = new DynamicGraph(nodes);
        int nextId = 0;
        for (int i = 0; i < count; i++) {
            DynamicEvent e = events.get(i);
            String[] p = e.payload().trim().split("\\s+");
            try {
                switch (e.type()) {
                    case ADD_EDGE:
                        g.addEdgeWithId(nextId++, Integer.parseInt(p[0]), Integer.parseInt(p[1]), Double.parseDouble(p[2]));
                        break;
                    case REMOVE_EDGE:
                        g.removeEdge(Integer.parseInt(p[0]));
                        break;
                    default:
                        g.setWeight(Integer.parseInt(p[0]), Double.parseDouble(p[1]));
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException bad) {
                throw new IllegalArgumentException("Event " + (i + 1) + " is damaged: " + e.type() + " \"" + e.payload() + "\"");
            }
        }
        return g;
    }
}
