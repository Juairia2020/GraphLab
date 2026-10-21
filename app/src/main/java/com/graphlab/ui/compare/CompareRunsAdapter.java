package com.graphlab.ui.compare;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.databinding.ItemCompareRunBinding;
import com.graphlab.engine.experiment.ExperimentRecord;

/** The experiments listed on the Compare-runs screen, each with its histogram. */
final class CompareRunsAdapter extends ListAdapter<CompareRunsAdapter.Row, CompareRunsAdapter.Holder> {

    /** One experiment as read from the ContentProvider. {@code record} is null if its detail could not be read. */
    static final class Row {
        final long id;
        final String title;
        final String meta;
        final String summary;
        @Nullable
        final ExperimentRecord record;

        Row(long id, @NonNull String title, @NonNull String meta, @NonNull String summary, @Nullable ExperimentRecord record) {
            this.id = id;
            this.title = title;
            this.meta = meta;
            this.summary = summary;
            this.record = record;
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.title.equals(b.title) && a.meta.equals(b.meta) && a.summary.equals(b.summary);
        }
    };

    CompareRunsAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCompareRunBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = getItem(position);
        holder.binding.compareItemTitle.setText(row.title);
        holder.binding.compareItemMeta.setText(row.meta);
        holder.binding.compareItemSummary.setText(row.summary);
        holder.binding.compareItemHistogram.setHistogram(row.record == null ? null : row.record.histogram());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemCompareRunBinding binding;

        Holder(@NonNull ItemCompareRunBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
