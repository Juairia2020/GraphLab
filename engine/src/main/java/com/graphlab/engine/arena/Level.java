package com.graphlab.engine.arena;

/**
 * The three levels of an Arena activity. A level only changes how big and how tricky the
 * generated problem is; the kinds of question stay the same. Plain Java, no Android.
 */
public enum Level {
    /** Small problems, the start node is always A. */
    EASY("Easy", 1, 5, 6, 1, 2, 9, 5, 3, 4, 8, 12),
    /** Medium graphs and lists, a random start node. */
    MEDIUM("Medium", 2, 6, 7, 2, 4, 15, 7, 4, 5, 10, 18),
    /** The biggest problems, with directed edges and negative weights where an algorithm allows them. */
    HARD("Hard", 3, 8, 9, 4, 6, 20, 9, 5, 6, 14, 25);

    private final String title;
    private final int multiplier;
    private final int minNodes;
    private final int maxNodes;
    private final int minExtraEdges;
    private final int maxExtraEdges;
    private final int maxWeight;
    private final int listSize;
    private final int minItems;
    private final int maxItems;
    private final int minCapacity;
    private final int maxCapacity;

    Level(String title, int multiplier, int minNodes, int maxNodes, int minExtraEdges, int maxExtraEdges,
            int maxWeight, int listSize, int minItems, int maxItems, int minCapacity, int maxCapacity) {
        this.title = title;
        this.multiplier = multiplier;
        this.minNodes = minNodes;
        this.maxNodes = maxNodes;
        this.minExtraEdges = minExtraEdges;
        this.maxExtraEdges = maxExtraEdges;
        this.maxWeight = maxWeight;
        this.listSize = listSize;
        this.minItems = minItems;
        this.maxItems = maxItems;
        this.minCapacity = minCapacity;
        this.maxCapacity = maxCapacity;
    }

    /** The name shown on buttons and headings. */
    public String title() {
        return title;
    }

    /** 1, 2 or 3: how many times the base points a correct answer is worth. */
    public int multiplier() {
        return multiplier;
    }

    /** Fewest nodes in a generated graph. */
    public int minNodes() {
        return minNodes;
    }

    /** Most nodes in a generated graph. */
    public int maxNodes() {
        return maxNodes;
    }

    /** Fewest edges added on top of the spanning tree that keeps the graph connected. */
    public int minExtraEdges() {
        return minExtraEdges;
    }

    public int maxExtraEdges() {
        return maxExtraEdges;
    }

    /** Largest edge weight (or capacity) a generated graph uses. */
    public int maxWeight() {
        return maxWeight;
    }

    /** How many numbers a sorting question has. */
    public int listSize() {
        return listSize;
    }

    public int minItems() {
        return minItems;
    }

    public int maxItems() {
        return maxItems;
    }

    public int minCapacity() {
        return minCapacity;
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    /** The next harder level, or null for {@link #HARD}. */
    public Level next() {
        return this == HARD ? null : values()[ordinal() + 1];
    }

    /** The level before this one, or null for {@link #EASY}. */
    public Level previous() {
        return this == EASY ? null : values()[ordinal() - 1];
    }
}
