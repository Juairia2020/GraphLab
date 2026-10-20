package com.graphlab.ui.lab;

import androidx.annotation.NonNull;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.play.Playback;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Trace;

/**
 * One finished run: the algorithm, the graph it ran on (a snapshot, so later edits cannot
 * change what is replayed), its trace and the play head over it. Owned by {@link LabViewModel}.
 */
public final class RunSession {
    public final Algorithm algorithm;
    public final Graph graph;
    public final Params params;
    public final Trace trace;
    public final Playback playback;
    /** Sorting and knapsack runs do not use the canvas graph, so editing it does not make them stale. */
    public boolean dependsOnGraph() {
        return algorithm.inputKind() == com.graphlab.engine.input.InputKind.GRAPH;
    }

    /** The editor's structureVersion when the run started; if it changed, the run is stale. */
    public final int structureVersion;

    RunSession(@NonNull Algorithm algorithm, @NonNull Graph graph, @NonNull Params params,
               @NonNull Trace trace, int structureVersion) {
        this.algorithm = algorithm;
        this.graph = graph;
        this.params = params;
        this.trace = trace;
        this.playback = new Playback(trace.size());
        this.structureVersion = structureVersion;
    }
}
