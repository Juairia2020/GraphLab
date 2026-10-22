package com.graphlab.ui.lab;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.databinding.ItemNodeRowBinding;
import com.graphlab.engine.play.StepSummary.NodeRow;
import com.graphlab.ui.common.StateColors;

/**
 * The node table of the State tab: a coloured dot, the node name, its state and its value (the
 * distance, the time). New rows arrive every step; DiffUtil redraws only the rows that changed.
 */
public final class NodeTableAdapter extends ListAdapter<NodeRow, NodeTableAdapter.Holder> {

    private static final DiffUtil.ItemCallback<NodeRow> DIFF = new DiffUtil.ItemCallback<NodeRow>() {
        @Override
        public boolean areItemsTheSame(@NonNull NodeRow a, @NonNull NodeRow b) {
            return a.nodeId() == b.nodeId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull NodeRow a, @NonNull NodeRow b) {
            return a.equals(b);   // a record: compares every field
        }
    };

    private int[] colors;

    public NodeTableAdapter(@NonNull int[] colors) {
        super(DIFF);
        this.colors = colors;
    }

    /** After a theme change the colours differ, so every row is redrawn. */
    public void setColors(@NonNull int[] colors) {
        this.colors = colors;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemNodeRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NodeRow row = getItem(position);
        holder.binding.nodeName.setText(row.name());
        holder.binding.nodeState.setText(StateColors.name(row.state()));
        holder.binding.nodeValue.setText(row.label().isEmpty() ? "–" : row.label());
        holder.binding.nodeDot.setBackgroundTintList(ColorStateList.valueOf(colors[row.state().ordinal()]));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemNodeRowBinding binding;

        Holder(@NonNull ItemNodeRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
