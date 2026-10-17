package com.graphlab.engine.experiment;

import com.graphlab.engine.model.Graph;

/**
 * The randomized algorithms the Experiment screen can repeat. Each one has an id (saved in the record), a title,
 * a one-line lesson, what the chart's numbers mean, and a way to make its {@link Trials}. Only Karger needs a graph;
 * the others work on a built-in input of {@link #size()} items.
 */
public enum ExperimentAlgorithm {
    KARGER("karger", "Karger min cut", true, 0,
            "Contract random edges until two groups remain. One run can miss the smallest cut; many runs find it.",
            "Cut sizes found", "best cut", "cut"),
    QUICK_SORT("quicksort", "Randomized quick sort", false, 100,
            "Sort 100 items that are already in order. A random pivot keeps the work near 9 comparisons per item; always taking the first item needs about 50.",
            "Comparisons per item", "fewest comparisons per item", "per item"),
    QUICK_SELECT("quickselect", "Randomized quick select (median)", false, 100,
            "Find the median of 100 shuffled items without sorting. About 3 to 4 comparisons per item on average.",
            "Comparisons per item", "fewest comparisons per item", "per item"),
    BALLS_IN_BINS("balls", "Random hashing (balls in bins)", false, 100,
            "Throw 100 balls into 100 bins at random. The average bin holds 1, but the fullest holds about 4 to 5.",
            "Load of the fullest bin", "lowest load", "load"),
    SECRETARY("secretary", "Hiring rule (secretary problem)", false, 100,
            "Skip the first 37 of 100 candidates, then hire the first one better than all of them. The very best is hired about 37% of the time.",
            "Rank of the person hired (1 is best)", "best rank", "rank");

    private final String id;
    private final String title;
    private final boolean needsGraph;
    private final int size;
    private final String lesson;
    private final String chartTitle;
    private final String bestLabel;
    private final String valueName;

    ExperimentAlgorithm(String id, String title, boolean needsGraph, int size, String lesson, String chartTitle,
                        String bestLabel, String valueName) {
        this.id = id;
        this.title = title;
        this.needsGraph = needsGraph;
        this.size = size;
        this.lesson = lesson;
        this.chartTitle = chartTitle;
        this.bestLabel = bestLabel;
        this.valueName = valueName;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** True when the experiment runs on a saved graph. */
    public boolean needsGraph() {
        return needsGraph;
    }

    /** The built-in input size for algorithms that need no graph (0 for Karger). */
    public int size() {
        return size;
    }

    public String lesson() {
        return lesson;
    }

    /** The heading above the histogram. */
    public String chartTitle() {
        return chartTitle;
    }

    /** The words used for the smallest value in a one-line summary, for example "best cut". */
    public String bestLabel() {
        return bestLabel;
    }

    /** The algorithm with this id; Karger for anything unknown (old records). */
    public static ExperimentAlgorithm byId(String id) {
        for (ExperimentAlgorithm a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return KARGER;
    }

    /**
     * @param graph the chosen graph (used only by Karger; may be null for the others)
     * @throws IllegalArgumentException when Karger gets no graph, or an unsuitable one
     */
    public Trials create(Graph graph) {
        switch (this) {
            case QUICK_SORT:
                return new QuickSortTrials(size);
            case QUICK_SELECT:
                return new QuickSelectTrials(size);
            case BALLS_IN_BINS:
                return new BallsInBinsTrials(size);
            case SECRETARY:
                return new SecretaryTrials(size);
            default:
                if (graph == null) {
                    throw new IllegalArgumentException("Karger min cut needs a graph");
                }
                return new KargerTrials(graph);
        }
    }
}
