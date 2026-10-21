package com.graphlab.ui.me;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.data.db.LogRecord;
import com.graphlab.databinding.ItemLogBinding;
import java.util.Date;

/** The rows of the lifecycle log: time, which Activity, what happened. */
public final class LogAdapter extends ListAdapter<LogRecord, LogAdapter.Holder> {

    private static final DiffUtil.ItemCallback<LogRecord> DIFF = new DiffUtil.ItemCallback<LogRecord>() {
        @Override
        public boolean areItemsTheSame(@NonNull LogRecord a, @NonNull LogRecord b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull LogRecord a, @NonNull LogRecord b) {
            return a.equals(b);
        }
    };

    public LogAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemLogBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LogRecord record = getItem(position);
        holder.binding.logTime.setText(DateFormat.format("HH:mm:ss", new Date(record.at)));
        holder.binding.logSource.setText(record.source.replace("Activity", ""));
        holder.binding.logEvent.setText(record.event);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemLogBinding binding;

        Holder(@NonNull ItemLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
