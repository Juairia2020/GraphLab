package com.graphlab.ui.arena;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.databinding.ItemOrderStepBinding;
import java.util.ArrayList;
import java.util.List;

/**
 * The list of steps in "Order the steps" (MS11). A step moves by its Up and Down buttons (which work with a screen reader) or
 * by a long press and drag. The adapter holds the order the player has built: {@link #order()} lists, for place 1, 2, 3 ..., the
 * index of the step in the task's item list, which is the form {@code Task.mistakes} reads.
 */
final class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.Holder> {
    /** Tells the screen the order changed. */
    interface Listener {
        void onOrderChanged();
    }

    private final List<String> items;
    private final List<Integer> order = new ArrayList<>();
    private final Listener listener;
    private boolean enabled = true;

    OrderAdapter(@NonNull List<String> items, @NonNull Listener listener) {
        this.items = items;
        this.listener = listener;
        for (int i = 0; i < items.size(); i++) {
            order.add(i);
        }
    }

    /** Lets a long press move a row. Call once after the adapter is set; the helper is returned so it can be detached. */
    ItemTouchHelper attachDrag(@NonNull RecyclerView list) {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder from,
                    @NonNull RecyclerView.ViewHolder to) {
                return enabled && move(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                // swiping is not used
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return enabled;
            }
        });
        helper.attachToRecyclerView(list);
        return helper;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        notifyItemRangeChanged(0, order.size());
    }

    /** The step index held at each place, first place first. */
    int[] order() {
        int[] out = new int[order.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = order.get(i);
        }
        return out;
    }

    private boolean move(int from, int to) {
        if (from < 0 || to < 0 || from >= order.size() || to >= order.size() || from == to) {
            return false;
        }
        int moved = order.remove(from);
        order.add(to, moved);
        notifyItemMoved(from, to);
        notifyItemRangeChanged(Math.min(from, to), Math.abs(from - to) + 1);   // the numbers and button states follow
        listener.onOrderChanged();
        return true;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemOrderStepBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.binding.stepNumber.setText(Integer.toString(position + 1));
        holder.binding.stepText.setText(items.get(order.get(position)));
        holder.binding.stepUp.setEnabled(enabled && position > 0);
        holder.binding.stepDown.setEnabled(enabled && position < order.size() - 1);
        holder.binding.stepUp.setOnClickListener(v -> move(holder.getBindingAdapterPosition(), holder.getBindingAdapterPosition() - 1));
        holder.binding.stepDown.setOnClickListener(v -> move(holder.getBindingAdapterPosition(), holder.getBindingAdapterPosition() + 1));
    }

    @Override
    public int getItemCount() {
        return order.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemOrderStepBinding binding;

        Holder(@NonNull ItemOrderStepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
