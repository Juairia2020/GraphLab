package com.graphlab.ui.online;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.databinding.ItemSampleBinding;

/** Draws the rows of the sample library and of the cloud gallery with the same card. */
final class PostAdapter extends ListAdapter<PostRow, PostAdapter.Holder> {

    interface Listener {
        void onAdd(@NonNull PostRow row);

        void onDelete(@NonNull PostRow row);
    }

    private final Listener listener;

    PostAdapter(@NonNull Listener listener) {
        super(new DiffUtil.ItemCallback<PostRow>() {
            @Override
            public boolean areItemsTheSame(@NonNull PostRow a, @NonNull PostRow b) {
                return a.id.equals(b.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull PostRow a, @NonNull PostRow b) {
                return a.title.equals(b.title) && a.meta.equals(b.meta) && a.description.equals(b.description)
                        && a.mine == b.mine;
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSampleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PostRow row = getItem(position);
        ItemSampleBinding b = holder.binding;
        b.sampleTitle.setText(row.title);
        b.sampleMeta.setText(row.meta);
        b.sampleDescription.setText(row.description);
        b.sampleDescription.setVisibility(row.description.isEmpty() ? View.GONE : View.VISIBLE);
        b.sampleThumb.setGraph(row.document.graph());
        b.sampleDelete.setVisibility(row.mine ? View.VISIBLE : View.GONE);
        b.sampleAdd.setOnClickListener(v -> listener.onAdd(row));
        b.sampleDelete.setOnClickListener(v -> listener.onDelete(row));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemSampleBinding binding;

        Holder(@NonNull ItemSampleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
