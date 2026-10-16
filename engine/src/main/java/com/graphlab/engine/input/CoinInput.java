package com.graphlab.engine.input;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A coin-change problem (MS12): coin values, each usable as often as needed, and the amount to make. At most
 * {@value #MAX_COINS} different coins with a value from 1 to {@value #MAX_COIN}, and an amount from 1 to
 * {@value #MAX_AMOUNT}, so the table (coins + 1 rows, amount + 1 columns) stays readable on a phone.
 */
public record CoinInput(List<Integer> coins, int amount) implements Input {
    public static final int MAX_COINS = 6;
    public static final int MAX_COIN = 20;
    public static final int MAX_AMOUNT = 20;

    public CoinInput {
        coins = List.copyOf(coins);
        if (coins.isEmpty()) {
            throw new IllegalArgumentException("Give at least one coin.");
        }
        if (coins.size() > MAX_COINS) {
            throw new IllegalArgumentException("At most " + MAX_COINS + " different coins fit on the screen, got " + coins.size() + ".");
        }
        Set<Integer> seen = new HashSet<>();
        for (int c : coins) {
            if (c < 1 || c > MAX_COIN) {
                throw new IllegalArgumentException("A coin must be worth between 1 and " + MAX_COIN + ", got " + c + ".");
            }
            if (!seen.add(c)) {
                throw new IllegalArgumentException("The coin " + c + " is listed twice.");
            }
        }
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("The amount must be between 1 and " + MAX_AMOUNT + ", got " + amount + ".");
        }
    }

    @Override
    public InputKind kind() {
        return InputKind.COINS;
    }
}
