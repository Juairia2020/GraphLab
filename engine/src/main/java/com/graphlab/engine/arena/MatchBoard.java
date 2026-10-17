package com.graphlab.engine.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The memory game behind "Match pairs" (MS11). Tiles are face down. The player flips two; if they are a term and its
 * meaning they stay up, otherwise they flip back and one mistake is counted. The board has no timing and no Android
 * code, so the rules are tested here and the screen only draws it.
 */
public final class MatchBoard {
    private final List<String> tiles = new ArrayList<>();   // what each position shows
    private final int[] pairOf;                              // pair number of each position
    private final boolean[] matched;
    private int first = -1;
    private int second = -1;
    private int mistakes;
    private int matchedPairs;

    /** @param task a MATCH task; the tiles are mixed with {@code seed}, so the same seed gives the same board */
    public MatchBoard(Task task, long seed) {
        if (task.interaction() != Interaction.MATCH) {
            throw new IllegalArgumentException("Not a match task");
        }
        int n = task.items().size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(seed));
        pairOf = new int[n];
        matched = new boolean[n];
        for (int position = 0; position < n; position++) {
            int item = order.get(position);
            tiles.add(task.items().get(item));
            pairOf[position] = item / 2;
        }
    }

    public int size() {
        return tiles.size();
    }

    public int pairs() {
        return tiles.size() / 2;
    }

    public String text(int position) {
        return tiles.get(position);
    }

    public boolean isMatched(int position) {
        return matched[position];
    }

    /** Face up now: matched, or one of the two just flipped. */
    public boolean isFaceUp(int position) {
        return matched[position] || position == first || position == second;
    }

    /** What a flip did. */
    public enum Result {
        /** Ignored: the tile is already up, or two wrong tiles are waiting to be turned back. */
        IGNORED,
        /** First tile of a try is up. */
        FIRST,
        /** The two tiles are a pair. */
        MATCH,
        /** The two tiles are not a pair; call {@link #flipBack()} after showing them. */
        MISMATCH
    }

    public Result flip(int position) {
        if (position < 0 || position >= tiles.size() || isFaceUp(position) || second >= 0) {
            return Result.IGNORED;
        }
        if (first < 0) {
            first = position;
            return Result.FIRST;
        }
        second = position;
        if (pairOf[first] == pairOf[second]) {
            matched[first] = true;
            matched[second] = true;
            matchedPairs++;
            first = -1;
            second = -1;
            return Result.MATCH;
        }
        mistakes++;
        return Result.MISMATCH;
    }

    /** Turns a wrong pair face down again. */
    public void flipBack() {
        first = -1;
        second = -1;
    }

    public boolean isDone() {
        return matchedPairs == pairs();
    }

    public int mistakes() {
        return mistakes;
    }

    /** The two tiles that are up and wrong, or null. */
    public int[] waitingPair() {
        return second >= 0 ? new int[] {first, second} : null;
    }
}
