package com.graphlab.engine.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mystery cards (MS17): three face-down cards, each hiding an activity. The activities are sampled without replacement, so no
 * activity is on two cards. The same pool and seed always give the same cards.
 */
public final class MysteryCards {
    public static final int CARDS = 3;

    private MysteryCards() {
    }

    /**
     * Draws {@value #CARDS} different activities from {@code pool} (fewer when the pool is smaller). A partial Fisher-Yates shuffle:
     * every activity has the same chance for every card.
     */
    public static List<QuestionKind> draw(List<QuestionKind> pool, long seed) {
        List<QuestionKind> rest = new ArrayList<>(new java.util.LinkedHashSet<>(pool));
        Random random = new Random(seed);
        List<QuestionKind> out = new ArrayList<>();
        while (out.size() < CARDS && !rest.isEmpty()) {
            out.add(rest.remove(random.nextInt(rest.size())));
        }
        return out;
    }
}
