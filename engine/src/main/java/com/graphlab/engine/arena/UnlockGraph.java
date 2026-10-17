package com.graphlab.engine.arena;

import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which activities are locked (MS17). Some activities need others first: an activity is open when every activity it requires has
 * been cleared (at least one star at any level). The requirements form a directed graph that must have no cycle; a
 * topological sort (Kahn's algorithm) gives an order in which every activity comes after the ones it requires, so the library
 * can be shown in the order a learner unlocks it. Activities with no entry are always open. Plain Java.
 */
public final class UnlockGraph {
    public static final String RESOURCE = "/content/unlocks.json";

    private final Map<QuestionKind, List<QuestionKind>> requires = new EnumMap<>(QuestionKind.class);
    private final List<QuestionKind> order;

    private UnlockGraph(Map<QuestionKind, List<QuestionKind>> requires) {
        this.requires.putAll(requires);
        this.order = sort(this.requires);
    }

    /** @throws IllegalArgumentException if a requirement lists the activity itself or the requirements contain a cycle */
    public static UnlockGraph of(Map<QuestionKind, List<QuestionKind>> requires) {
        for (Map.Entry<QuestionKind, List<QuestionKind>> e : requires.entrySet()) {
            if (e.getValue().contains(e.getKey())) {
                throw new IllegalArgumentException(e.getKey().id() + " cannot require itself");
            }
        }
        return new UnlockGraph(requires);
    }

    /** The graph in the bundled {@code unlocks.json}. */
    public static UnlockGraph bundled() {
        try (InputStream in = UnlockGraph.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled unlock file is missing: " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("The bundled unlock file cannot be read: " + e.getMessage(), e);
        }
    }

    /** @throws IllegalArgumentException for bad JSON, an unknown activity id, or a cycle */
    public static UnlockGraph parse(String text) {
        Object root;
        try {
            root = MiniJson.parse(text);
        } catch (GraphFormatException e) {
            throw new IllegalArgumentException("The unlock file is not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map) || !(((Map<?, ?>) root).get("requires") instanceof Map)) {
            throw new IllegalArgumentException("The unlock file needs an object \"requires\".");
        }
        Map<QuestionKind, List<QuestionKind>> out = new EnumMap<>(QuestionKind.class);
        for (Map.Entry<?, ?> e : ((Map<?, ?>) ((Map<?, ?>) root).get("requires")).entrySet()) {
            QuestionKind kind = QuestionKind.findById(String.valueOf(e.getKey()));
            if (kind == null) {
                throw new IllegalArgumentException("Unknown activity in the unlock file: " + e.getKey());
            }
            if (!(e.getValue() instanceof List)) {
                throw new IllegalArgumentException("\"" + e.getKey() + "\" must list the activities it requires.");
            }
            List<QuestionKind> needs = new ArrayList<>();
            for (Object o : (List<?>) e.getValue()) {
                QuestionKind need = QuestionKind.findById(String.valueOf(o));
                if (need == null) {
                    throw new IllegalArgumentException("Unknown activity in the unlock file: " + o);
                }
                if (!needs.contains(need)) {
                    needs.add(need);
                }
            }
            out.put(kind, needs);
        }
        return of(out);
    }

    /** Kahn's algorithm; ties go to the activity listed first in {@link QuestionKind}, so the order is the same every time. */
    private static List<QuestionKind> sort(Map<QuestionKind, List<QuestionKind>> requires) {
        Map<QuestionKind, Integer> waiting = new EnumMap<>(QuestionKind.class);
        for (QuestionKind k : QuestionKind.values()) {
            waiting.put(k, requires.getOrDefault(k, List.of()).size());
        }
        List<QuestionKind> out = new ArrayList<>();
        Set<QuestionKind> done = new LinkedHashSet<>();
        ArrayDeque<QuestionKind> ready = new ArrayDeque<>();
        while (out.size() < QuestionKind.values().length) {
            ready.clear();
            for (QuestionKind k : QuestionKind.values()) {
                if (!done.contains(k) && waiting.get(k) == 0) {
                    ready.add(k);
                }
            }
            if (ready.isEmpty()) {
                throw new IllegalArgumentException("The unlock requirements contain a cycle.");
            }
            QuestionKind next = ready.poll();   // the first ready one in enum order
            done.add(next);
            out.add(next);
            for (QuestionKind k : QuestionKind.values()) {
                if (requires.getOrDefault(k, List.of()).contains(next)) {
                    waiting.merge(k, -1, Integer::sum);
                }
            }
        }
        return List.copyOf(out);
    }

    /** Every activity, each after the activities it requires. */
    public List<QuestionKind> order() {
        return order;
    }

    public List<QuestionKind> requirements(QuestionKind kind) {
        return List.copyOf(requires.getOrDefault(kind, List.of()));
    }

    /** True when every required activity is cleared. */
    public boolean isUnlocked(QuestionKind kind, Predicate<QuestionKind> cleared) {
        for (QuestionKind need : requirements(kind)) {
            if (!cleared.test(need)) {
                return false;
            }
        }
        return true;
    }

    /** The required activities not yet cleared, for the text on a locked tile. */
    public List<QuestionKind> missing(QuestionKind kind, Predicate<QuestionKind> cleared) {
        List<QuestionKind> out = new ArrayList<>();
        for (QuestionKind need : requirements(kind)) {
            if (!cleared.test(need)) {
                out.add(need);
            }
        }
        return out;
    }

    /** The open activities, in unlock order. */
    public List<QuestionKind> unlocked(Predicate<QuestionKind> cleared) {
        List<QuestionKind> out = new ArrayList<>();
        for (QuestionKind k : order) {
            if (isUnlocked(k, cleared)) {
                out.add(k);
            }
        }
        return out;
    }

    /** The activities that open only because {@code kind} was just cleared ({@code before} says what was cleared until then). */
    public List<QuestionKind> unlockedBy(QuestionKind kind, Predicate<QuestionKind> before) {
        Predicate<QuestionKind> after = k -> k == kind || before.test(k);
        List<QuestionKind> out = new ArrayList<>();
        for (QuestionKind k : order) {
            if (!isUnlocked(k, before) && isUnlocked(k, after)) {
                out.add(k);
            }
        }
        return out;
    }

    /** Cleared means at least one star at any level. */
    public static Predicate<QuestionKind> clearedIn(ArenaProgress progress) {
        return k -> progress.starsOf(k) >= 1;
    }
}
