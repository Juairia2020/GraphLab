package com.graphlab.engine.arena;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** The shown options of a question and the position of the right one. Also helpers that invent wrong answers. */
final class Choices {
    final List<String> options;
    final int correctIndex;

    private Choices(List<String> options, int correctIndex) {
        this.options = options;
        this.correctIndex = correctIndex;
    }

    /** Puts the right answer among the wrong ones in a random place. Wrong answers equal to the right one are dropped. */
    static Choices of(Random random, String correct, List<String> wrong, int wanted) {
        Set<String> picked = new LinkedHashSet<>();
        for (String w : wrong) {
            if (!w.equals(correct) && picked.size() < wanted - 1) {
                picked.add(w);
            }
        }
        List<String> all = new ArrayList<>(picked);
        all.add(correct);
        Collections.shuffle(all, random);
        return new Choices(List.copyOf(all), all.indexOf(correct));
    }

    /** Options in a fixed order (for Yes / No), with the right one at {@code correctIndex}. */
    static Choices fixed(List<String> options, int correctIndex) {
        return new Choices(List.copyOf(options), correctIndex);
    }

    /** True when {@code wrong} (with the right answer left out) holds enough different options. */
    static boolean enough(String correct, List<String> wrong, int wanted) {
        Set<String> picked = new LinkedHashSet<>(wrong);
        picked.remove(correct);
        return picked.size() >= wanted - 1;
    }

    // ---- numbers ---------------------------------------------------------------------

    /** "7" for a whole number, "7.5" or "7.25" otherwise (two decimals at most). */
    static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return Long.toString(Math.round(value));
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * Wrong numbers for a question whose answer is a number: first the "clever" mistakes (what a common
     * wrong method gives), then numbers close to the right one, then any other number above {@code min}.
     */
    static List<String> wrongNumbers(Random random, double correct, List<Double> clever, double min, double span) {
        Set<String> out = new LinkedHashSet<>();
        String right = number(correct);
        for (double c : clever) {
            if (c >= min && Double.isFinite(c) && !number(c).equals(right)) {
                out.add(number(c));
            }
        }
        List<Double> near = new ArrayList<>();
        for (int d = 1; d <= 4; d++) {
            near.add(correct + d);
            near.add(correct - d);
        }
        Collections.shuffle(near, random);
        for (double v : near) {
            if (v >= min && !number(v).equals(right)) {
                out.add(number(v));
            }
        }
        int guard = 0;
        while (out.size() < 6 && guard++ < 200) {
            double v = Math.max(min, Math.round(correct + (random.nextDouble() * 2 - 1) * span));
            if (!number(v).equals(right)) {
                out.add(number(v));
            }
        }
        return new ArrayList<>(out);
    }

    // ---- orders ----------------------------------------------------------------------

    /** Wrong orders of the same nodes: the clever ones first, then swaps in the right order. */
    static List<List<Integer>> wrongOrders(Random random, List<Integer> correct, List<List<Integer>> clever) {
        Set<List<Integer>> out = new LinkedHashSet<>();
        for (List<Integer> c : clever) {
            if (c.size() == correct.size() && !c.equals(correct)) {
                out.add(List.copyOf(c));
            }
        }
        int guard = 0;
        while (out.size() < 6 && guard++ < 300 && correct.size() >= 2) {
            List<Integer> copy = new ArrayList<>(correct);
            int swaps = 1 + random.nextInt(2);
            for (int s = 0; s < swaps; s++) {
                int i = random.nextInt(copy.size() - 1);
                int j = random.nextBoolean() ? i + 1 : random.nextInt(copy.size());
                Collections.swap(copy, i, j);
            }
            if (!copy.equals(correct)) {
                out.add(copy);
            }
        }
        return new ArrayList<>(out);
    }
}
