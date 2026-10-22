package com.graphlab.ui.imports;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.databinding.RowImportFlagBinding;
import com.graphlab.engine.imageimport.ReviewSession;
import java.util.ArrayList;
import java.util.List;

/** The amber list on the import review screen (MS16): one row per doubtful item, with a Fix button. */
final class FlagAdapter extends RecyclerView.Adapter<FlagAdapter.Holder> {
    interface Listener {
        void onFix(@NonNull ReviewSession.Flag flag);
    }

    private final Listener listener;
    private List<ReviewSession.Flag> flags = new ArrayList<>();

    FlagAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void submit(@NonNull List<ReviewSession.Flag> next) {
        flags = new ArrayList<>(next);
        notifyDataSetChanged();   // the list is short and changes after every fix
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(RowImportFlagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final ReviewSession.Flag flag = flags.get(position);
        holder.binding.flagMessage.setText(flag.message());
        holder.binding.flagFix.setOnClickListener(v -> listener.onFix(flag));
        holder.binding.getRoot().setOnClickListener(v -> listener.onFix(flag));
    }

    @Override
    public int getItemCount() {
        return flags.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final RowImportFlagBinding binding;

        Holder(@NonNull RowImportFlagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
