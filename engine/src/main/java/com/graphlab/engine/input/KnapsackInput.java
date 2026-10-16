package com.graphlab.engine.input;

import java.util.List;

/**
 * A knapsack problem: items with a weight and a value, and a bag that holds at most
 * {@code capacity} weight. At most {@value #MAX_ITEMS} items and a capacity of at most
 * {@value #MAX_CAPACITY}, so the dynamic-programming table (items + 1 rows, capacity + 1
 * columns) stays readable.
 */
public record KnapsackInput(List<Item> items, int capacity) implements Input {
    public static final int MAX_ITEMS = 8;
    public static final int MAX_CAPACITY = 30;

    public KnapsackInput {
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Give at least one item.");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("At most " + MAX_ITEMS + " items fit on the screen, got " + items.size() + ".");
        }
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("The bag capacity must be between 1 and " + MAX_CAPACITY + ", got " + capacity + ".");
        }
    }

    @Override
    public InputKind kind() {
        return InputKind.KNAPSACK;
    }
}
