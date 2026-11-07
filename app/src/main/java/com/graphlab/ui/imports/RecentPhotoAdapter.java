package com.graphlab.ui.imports;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.graphlab.R;
import com.graphlab.data.imports.RecentPhotos;
import com.graphlab.databinding.ItemRecentPhotoBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** The horizontal strip of recent pictures on the import screen. Each ViewHolder loads its own thumbnail in the background. */
final class RecentPhotoAdapter extends RecyclerView.Adapter<RecentPhotoAdapter.Holder> {
    interface Listener {
        void onPhoto(@NonNull RecentPhotos.Photo photo);
    }

    private static final int THUMB_PX = 160;

    private final ContentResolver resolver;
    private final ExecutorService executor;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<RecentPhotos.Photo> photos = new ArrayList<>();

    RecentPhotoAdapter(@NonNull ContentResolver resolver, @NonNull ExecutorService executor, @NonNull Listener listener) {
        this.resolver = resolver;
        this.executor = executor;
        this.listener = listener;
    }

    void submit(@NonNull List<RecentPhotos.Photo> next) {
        photos = new ArrayList<>(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemRecentPhotoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final RecentPhotos.Photo photo = photos.get(position);
        holder.binding.recentThumb.setImageDrawable(null);
        holder.binding.recentThumb.setTag(photo.uri);
        holder.binding.recentThumb.setContentDescription(
                holder.itemView.getContext().getString(R.string.recent_item_description, photo.name));
        holder.binding.getRoot().setOnClickListener(v -> listener.onPhoto(photo));
        executor.execute(() -> {
            final Bitmap bitmap = RecentPhotos.thumbnail(resolver, photo.uri, THUMB_PX);
            main.post(() -> {
                // The holder may have been recycled for another picture meanwhile.
                if (bitmap != null && photo.uri.equals(holder.binding.recentThumb.getTag())) {
                    holder.binding.recentThumb.setImageBitmap(bitmap);
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemRecentPhotoBinding binding;

        Holder(@NonNull ItemRecentPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
