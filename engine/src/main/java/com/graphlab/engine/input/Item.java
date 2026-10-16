package com.graphlab.engine.input;

/** One thing that can go in the bag: how heavy it is and how much it is worth. */
public record Item(int weight, int value) {
    public static final int MAX_WEIGHT = 20;
    public static final int MAX_VALUE = 99;

    public Item {
        if (weight < 1 || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("An item's weight must be between 1 and " + MAX_WEIGHT + ", got " + weight + ".");
        }
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("An item's value must be between 0 and " + MAX_VALUE + ", got " + value + ".");
        }
    }
}
