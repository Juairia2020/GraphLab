package com.graphlab.ui.lab;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.R;
import com.graphlab.data.db.GraphSummary;
import com.graphlab.databinding.ItemGraphBinding;
import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.model.Graph;

/**
 * The library list. Each row shows a thumbnail drawn from the graph's JSON, the name, the size,
 * when it changed and how often it was run. Parsed graphs are cached by id and change time, so
 * scrolling does not parse the same JSON again.
 */
public final class GraphRowAdapter extends ListAdapter<GraphSummary, GraphRowAdapter.Holder> {

    /** What the screen does for a row. */
    public interface Listener {
        void onOpen(@NonNull GraphSummary graph);

        void onMenu(@NonNull GraphSummary graph, @NonNull android.view.View anchor);
    }

    private static final DiffUtil.ItemCallback<GraphSummary> DIFF = new DiffUtil.ItemCallback<GraphSummary>() {
        @Override
        public boolean areItemsTheSame(@NonNull GraphSummary a, @NonNull GraphSummary b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull GraphSummary a, @NonNull GraphSummary b) {
            return a.equals(b);
        }
    };

    private final Listener listener;
    private final LruCache<String, Graph> parsed = new LruCache<>(48);

    public GraphRowAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** The summary at a position (used by swipe-to-delete). */
    @NonNull
    public GraphSummary graphAt(int position) {
        return getItem(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemGraphBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GraphSummary graph = getItem(position);
        Context context = holder.itemView.getContext();
        holder.binding.graphTitle.setText(graph.name);
        holder.binding.graphDetails.setText(context.getString(R.string.graph_row_details,
                context.getResources().getQuantityString(R.plurals.nodes, graph.nodeCount, graph.nodeCount),
                context.getResources().getQuantityString(R.plurals.edges, graph.edgeCount, graph.edgeCount)));
        CharSequence when = DateUtils.getRelativeTimeSpanString(graph.updatedAt, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS);
        String meta = context.getString(R.string.graph_row_updated, when);
        if (graph.runCount > 0) {
            meta += " · " + context.getResources().getQuantityString(R.plurals.graph_row_runs, graph.runCount,
                    graph.runCount, algorithmName(graph.lastAlgorithm));
        }
        holder.binding.graphMeta.setText(meta);
        holder.binding.graphThumb.setGraph(graphOf(graph));
        holder.binding.getRoot().setContentDescription(context.getString(R.string.graph_row_description, graph.name));
        holder.binding.getRoot().setOnClickListener(v -> listener.onOpen(graph));
        holder.binding.graphMenu.setOnClickListener(v -> listener.onMenu(graph, v));
    }

    @Nullable
    private Graph graphOf(@NonNull GraphSummary summary) {
        String key = summary.id + ":" + summary.updatedAt;
        Graph cached = parsed.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Graph graph = GraphJson.read(summary.json).graph();
            parsed.put(key, graph);
            return graph;
        } catch (Exception e) {
            return null;   // a damaged row still shows, just without a picture
        }
    }

    @NonNull
    private static String algorithmName(@Nullable String id) {
        if (id == null) {
            return "";
        }
        try {
            return Algorithms.byId(id).name();
        } catch (RuntimeException e) {
            return id;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemGraphBinding binding;

        Holder(@NonNull ItemGraphBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
