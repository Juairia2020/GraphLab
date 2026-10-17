package com.graphlab.engine.arena;

import java.util.List;
import java.util.Random;

/**
 * Puts one known mistake into one line of an algorithm's pseudocode (MS11, "Fix the pseudocode"). Each rule swaps a word or a
 * sign for its opposite, so the broken line is still readable but now does the wrong thing. Only the first rule that fits a
 * line is used, and exactly one line is changed.
 */
public final class PseudocodeFaults {
    private PseudocodeFaults() {
    }

    /** One rule: what to find, what to put instead, and why the new text is wrong. */
    private record Rule(String find, String replace, String why) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("smallest", "largest", "it must take the smallest, not the largest"),
            new Rule("lightest", "heaviest", "it must take the lightest edges first, not the heaviest"),
            new Rule("cheapest", "most expensive", "it must take the cheapest, not the most expensive"),
            new Rule("larger of", "smaller of", "it must keep the larger of the two"),
            new Rule("smaller front", "larger front", "it must take the smaller front element"),
            new Rule("different sets", "the same set", "an edge joins two different sets; inside one set it would close a cycle"),
            new Rule(" < ", " > ", "the comparison points the wrong way"),
            new Rule(" <= ", " >= ", "the comparison points the wrong way"),
            new Rule(" >= ", " <= ", "the comparison points the wrong way"),
            new Rule("is already", "is not yet", "the test is turned round"),
            new Rule("is not less than", "is less than", "the test is turned round"),
            new Rule("most first", "fewest first", "the nodes with the most neighbours must come first"),
            new Rule(" + 1", " - 1", "the count must go up by one"),
            new Rule(" - 1", " + 1", "it must be one less"),
            new Rule("dist[u] + w", "dist[u] - w", "a path gets longer by the weight of the edge"));

    /** The pseudocode with one line broken. */
    public record Fault(List<String> lines, int lineIndex, String original, String broken, String why) {
    }

    /** Which lines can be broken. */
    public static List<Integer> breakable(List<String> pseudocode) {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < pseudocode.size(); i++) {
            if (ruleFor(pseudocode.get(i)) != null) {
                out.add(i);
            }
        }
        return out;
    }

    private static Rule ruleFor(String line) {
        for (Rule rule : RULES) {
            if (line.contains(rule.find())) {
                return rule;
            }
        }
        return null;
    }

    /** @return a fault in one of the breakable lines, or null if no line can be broken */
    public static Fault inject(List<String> pseudocode, Random random) {
        List<Integer> candidates = breakable(pseudocode);
        if (candidates.isEmpty()) {
            return null;
        }
        int index = candidates.get(random.nextInt(candidates.size()));
        String original = pseudocode.get(index);
        Rule rule = ruleFor(original);
        String broken = original.replaceFirst(java.util.regex.Pattern.quote(rule.find()),
                java.util.regex.Matcher.quoteReplacement(rule.replace()));
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(pseudocode);
        lines.set(index, broken);
        return new Fault(lines, index, original, broken, rule.why());
    }
}
