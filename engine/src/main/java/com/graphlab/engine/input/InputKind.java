package com.graphlab.engine.input;

/** What an algorithm works on. The Run screen shows the matching input control. */
public enum InputKind {
    /** The graph on the canvas. */
    GRAPH,
    /** A short list of whole numbers (sorting). */
    ARRAY,
    /** A set of items with a weight and a value, and a bag capacity. */
    KNAPSACK,
    /** Text only: a statement or a situation (the card activities of the Arena). */
    TEXT,
    /** Coin values and a target amount (coin change). */
    COINS,
    /** Two short pieces of text (longest common subsequence, edit distance). */
    STRINGS
}
