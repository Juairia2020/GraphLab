package com.graphlab.engine.input;

/**
 * Whatever an algorithm is run on: a graph, a list of numbers or a knapsack problem. Sealed so
 * the screens can handle every kind. Each concrete input checks its own limits when it is
 * created, so an algorithm never sees a list that is too long to show.
 */
public sealed interface Input permits GraphInput, ArrayInput, KnapsackInput, TextInput, CoinInput, StringsInput {
    InputKind kind();
}
