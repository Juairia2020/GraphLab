package com.graphlab.engine.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a person typed into an {@link Input}, and makes random and default inputs. Every
 * failure is an {@link IllegalArgumentException} whose message can be shown as it is.
 */
public final class InputParser {
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,;]+");
    private static final Pattern ITEM = Pattern.compile("(\\d+)[:/x](\\d+)");

    private InputParser() {
    }

    /** "5 2 9 1" or "5, 2, 9, 1". */
    public static ArrayInput parseArray(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Type some numbers, for example: 5 2 9 1 7");
        }
        List<Integer> values = new ArrayList<>();
        for (String token : SEPARATORS.split(trimmed)) {
            if (token.isEmpty()) {
                continue;
            }
            values.add(wholeNumber(token));
        }
        return new ArrayInput(values);
    }

    /**
     * Items as "weight:value" pairs, for example "1:1 3:4 4:5 5:7", and the bag capacity as text.
     */
    public static KnapsackInput parseKnapsack(String itemsText, String capacityText) {
        String trimmed = itemsText == null ? "" : itemsText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Type the items as weight:value pairs, for example: 1:1 3:4 4:5 5:7");
        }
        List<Item> items = new ArrayList<>();
        for (String token : SEPARATORS.split(trimmed)) {
            if (token.isEmpty()) {
                continue;
            }
            Matcher m = ITEM.matcher(token);
            if (!m.matches()) {
                throw new IllegalArgumentException("'" + token + "' is not a weight:value pair like 3:4.");
            }
            items.add(new Item(wholeNumber(m.group(1)), wholeNumber(m.group(2))));
        }
        String cap = capacityText == null ? "" : capacityText.trim();
        if (cap.isEmpty()) {
            throw new IllegalArgumentException("Type the bag capacity, for example: 7");
        }
        return new KnapsackInput(items, wholeNumber(cap));
    }

    /** Coins as "1 3 4" and the amount as text (MS12). */
    public static CoinInput parseCoins(String coinsText, String amountText) {
        String trimmed = coinsText == null ? "" : coinsText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Type the coin values, for example: 1 3 4");
        }
        List<Integer> coins = new ArrayList<>();
        for (String token : SEPARATORS.split(trimmed)) {
            if (!token.isEmpty()) {
                coins.add(wholeNumber(token));
            }
        }
        String amount = amountText == null ? "" : amountText.trim();
        if (amount.isEmpty()) {
            throw new IllegalArgumentException("Type the amount to make, for example: 6");
        }
        return new CoinInput(coins, wholeNumber(amount));
    }

    /** Two pieces of text for LCS and edit distance (MS12). */
    public static StringsInput parseStrings(String first, String second) {
        return new StringsInput(first, second);
    }

    /** Text for the coins, the inverse of {@link #parseCoins}. */
    public static String formatCoins(CoinInput input) {
        StringBuilder out = new StringBuilder();
        for (int c : input.coins()) {
            out.append(out.length() == 0 ? "" : " ").append(c);
        }
        return out.toString();
    }

    /** Text for an array, the inverse of {@link #parseArray}. */
    public static String format(ArrayInput input) {
        StringBuilder out = new StringBuilder();
        for (int v : input.values()) {
            out.append(out.length() == 0 ? "" : " ").append(v);
        }
        return out.toString();
    }

    /** Text for the items of a knapsack, the inverse of {@link #parseKnapsack}. */
    public static String formatItems(KnapsackInput input) {
        StringBuilder out = new StringBuilder();
        for (Item item : input.items()) {
            out.append(out.length() == 0 ? "" : " ").append(item.weight()).append(':').append(item.value());
        }
        return out.toString();
    }

    // ---- defaults and random inputs ---------------------------------------------------

    /** A small classic list with a duplicate-free spread, good for showing every sort. */
    public static ArrayInput defaultArray() {
        return new ArrayInput(List.of(38, 27, 43, 3, 9, 82, 10));
    }

    /** The textbook example: weights 1, 3, 4, 5, values 1, 4, 5, 7, capacity 7 (best value 9). */
    public static KnapsackInput defaultKnapsack() {
        return new KnapsackInput(List.of(new Item(1, 1), new Item(3, 4), new Item(4, 5), new Item(5, 7)), 7);
    }

    /** Coins 1, 3 and 4 for 6: greedy takes 4 + 1 + 1 (three coins) but the best is 3 + 3 (two coins). */
    public static CoinInput defaultCoins() {
        return new CoinInput(List.of(1, 3, 4), 6);
    }

    /** The textbook pair for the longest common subsequence: ABCBDAB and BDCABA (the answer has 4 letters). */
    public static StringsInput defaultStrings() {
        return new StringsInput("ABCBDAB", "BDCABA");
    }

    /** The classic edit-distance pair: KITTEN and SITTING (distance 3). */
    public static StringsInput defaultEditStrings() {
        return new StringsInput("KITTEN", "SITTING");
    }

    /** A list for the longest increasing subsequence: 10 9 2 5 3 7 60 18 (the answer has 4 numbers). */
    public static ArrayInput defaultSequence() {
        return new ArrayInput(List.of(10, 9, 2, 5, 3, 7, 60, 18));
    }

    /** Two to five random coins (1 to 9) and an amount of 8 to 20. The same seed gives the same problem. */
    public static CoinInput randomCoins(long seed) {
        Random random = new Random(seed);
        int n = 2 + random.nextInt(4);
        List<Integer> pool = new ArrayList<>();
        for (int v = 1; v <= 9; v++) {
            pool.add(v);
        }
        java.util.Collections.shuffle(pool, random);
        return new CoinInput(new ArrayList<>(pool.subList(0, n)), 8 + random.nextInt(13));
    }

    /** Two random words of 5 to 7 letters from a small alphabet, so they share letters. */
    public static StringsInput randomStrings(long seed) {
        Random random = new Random(seed);
        return new StringsInput(randomWord(random, 5 + random.nextInt(3)), randomWord(random, 5 + random.nextInt(3)));
    }

    private static String randomWord(Random random, int length) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            out.append((char) ('A' + random.nextInt(4)));
        }
        return out.toString();
    }

    /** {@code count} random numbers from 1 to 99. The same seed gives the same list. */
    public static ArrayInput randomArray(long seed, int count) {
        int n = Math.max(1, Math.min(ArrayInput.MAX_SIZE, count));
        Random random = new Random(seed);
        List<Integer> values = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            values.add(1 + random.nextInt(ArrayInput.MAX_VALUE));
        }
        return new ArrayInput(values);
    }

    /** Random items (weight 1 to 12, value 5 to 60) and a capacity of about half the total weight. */
    public static KnapsackInput randomKnapsack(long seed, int count) {
        int n = Math.max(1, Math.min(KnapsackInput.MAX_ITEMS, count));
        Random random = new Random(seed);
        List<Item> items = new ArrayList<>(n);
        int total = 0;
        for (int i = 0; i < n; i++) {
            int w = 1 + random.nextInt(12);
            items.add(new Item(w, 5 + random.nextInt(56)));
            total += w;
        }
        int capacity = Math.max(1, Math.min(KnapsackInput.MAX_CAPACITY, total / 2));
        return new KnapsackInput(items, capacity);
    }

    private static int wholeNumber(String token) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + token + "' is not a whole number.");
        }
    }
}
