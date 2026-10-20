package com.graphlab.ui.lab;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.R;
import com.graphlab.databinding.ItemStepBinding;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.List;

/**
 * The step log: one row per step of the trace, the current one highlighted. A row is the step
 * number, the pseudocode line it belongs to and the plain-English message. Tapping a row jumps
 * to that step.
 */
public final class StepLogAdapter extends ListAdapter<StepLogAdapter.Row, StepLogAdapter.Holder> {

    /** Called with the step index when a row is tapped. */
    public interface OnStepClick {
        void onStepClick(int index);
    }

    /** One row. A plain class with equals(), so DiffUtil can tell when nothing changed. */
    public static final class Row {
        final int index;
        final int line;
        @NonNull
        final String message;

        Row(int index, int line, @NonNull String message) {
            this.index = index;
            this.line = line;
            this.message = message;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Row)) {
                return false;
            }
            Row that = (Row) other;
            return index == that.index && line == that.line && message.equals(that.message);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * index + line) + message.hashCode();
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.index == b.index;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.equals(b);
        }
    };

    private final OnStepClick onClick;
    private int current = -1;

    public StepLogAdapter(@NonNull OnStepClick onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    /** The rows for a trace. */
    @NonNull
    public static List<Row> rowsOf(@NonNull Trace trace) {
        List<Row> rows = new ArrayList<>(trace.size());
        for (int i = 0; i < trace.size(); i++) {
            rows.add(new Row(i, trace.step(i).line(), trace.step(i).message()));
        }
        return rows;
    }

    /** Highlights one step. Only the two rows that change are redrawn. */
    public void setCurrent(int index) {
        if (index == current) {
            return;
        }
        int old = current;
        current = index;
        if (old >= 0 && old < getItemCount()) {
            notifyItemChanged(old);
        }
        if (index >= 0 && index < getItemCount()) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStepBinding binding = ItemStepBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = getItem(position);
        holder.binding.stepNumber.setText(String.valueOf(row.index + 1));
        holder.binding.stepMessage.setText(row.message);
        holder.binding.stepLine.setText(holder.itemView.getContext().getString(R.string.line_label, row.line));
        holder.binding.getRoot().setSelected(row.index == current);
        holder.binding.getRoot().setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onClick.onStepClick(getItem(adapterPosition).index);
            }
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemStepBinding binding;

        Holder(@NonNull ItemStepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
