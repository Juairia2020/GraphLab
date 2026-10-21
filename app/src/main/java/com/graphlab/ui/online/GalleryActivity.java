package com.graphlab.ui.online;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.GraphImporter;
import com.graphlab.data.cloud.CloudGallery;
import com.graphlab.data.db.GraphSummary;
import com.graphlab.data.prefs.CloudPrefs;
import com.graphlab.databinding.ActivityGalleryBinding;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFiles;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.net.GalleryPost;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The cloud gallery: graphs that people using the app have shared. Anyone signed in (anonymously)
 * can read, add a shared graph to their own library, and publish one of their saved graphs; only
 * the publisher can delete a post. If this build has no Firebase project configured, the screen
 * says how to set one up instead of failing.
 */
public class GalleryActivity extends AppCompatActivity implements PostAdapter.Listener, GalleryViewModel.Listener {
    private ActivityGalleryBinding binding;
    private GalleryViewModel model;
    private PostAdapter adapter;
    private AppContainer container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        container = GraphLabApp.container(this);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });

        model = new ViewModelProvider(this).get(GalleryViewModel.class);
        adapter = new PostAdapter(this);
        binding.galleryList.setLayoutManager(new LinearLayoutManager(this));
        binding.galleryList.setAdapter(adapter);
        binding.galleryList.setItemAnimator(null);
        binding.galleryBack.setOnClickListener(v -> finish());
        binding.galleryRefresh.setOnClickListener(v -> model.refresh());
        binding.galleryPublish.setOnClickListener(v -> choosePublication());
    }

    @Override
    protected void onStart() {
        super.onStart();
        model.setListener(this);
        model.start(this);
        render();
    }

    @Override
    protected void onStop() {
        model.setListener(null);
        super.onStop();
    }

    @Override
    public void onChanged() {
        render();
    }

    private void render() {
        GalleryViewModel.State state = model.state();
        boolean busy = state == GalleryViewModel.State.SIGNING_IN || state == GalleryViewModel.State.LOADING;
        binding.galleryProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.galleryRefresh.setEnabled(!busy && state != GalleryViewModel.State.NOT_CONFIGURED);
        binding.galleryPublish.setVisibility(state == GalleryViewModel.State.READY ? View.VISIBLE : View.GONE);
        binding.galleryStatus.setText(state == GalleryViewModel.State.READY
                ? getString(R.string.gallery_status_ready, CloudPrefs.displayName(this))
                : "");
        binding.galleryStatus.setVisibility(state == GalleryViewModel.State.READY ? View.VISIBLE : View.GONE);

        List<PostRow> rows = new ArrayList<>();
        for (GalleryPost.Entry entry : model.entries()) {
            String age = DateUtils.getRelativeTimeSpanString(entry.createdAt(), System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString();
            rows.add(new PostRow(entry.id(), entry.title(),
                    getString(R.string.gallery_meta, entry.author(),
                            getResources().getQuantityString(R.plurals.nodes, entry.nodeCount(), entry.nodeCount()),
                            getResources().getQuantityString(R.plurals.edges, entry.edgeCount(), entry.edgeCount()), age),
                    "", entry.document(), entry.isOwnedBy(model.uid())));
        }
        adapter.submitList(rows);

        String message;
        switch (state) {
            case NOT_CONFIGURED:
                message = getString(R.string.gallery_not_configured);
                break;
            case ERROR:
                message = model.error();
                break;
            case READY:
                message = rows.isEmpty() ? getString(R.string.gallery_empty) : "";
                break;
            default:
                message = "";
        }
        binding.galleryMessage.setText(message);
        binding.galleryMessage.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- add to my graphs ----

    @Override
    public void onAdd(@NonNull PostRow row) {
        GraphImporter.add(container, row.document, row.title, new AppExecutors.Callback<String>() {
            @Override
            public void onSuccess(String name) {
                snack(getString(R.string.samples_added, name));
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.samples_add_failed));
            }
        });
    }

    // ---- delete my post ----

    @Override
    public void onDelete(@NonNull PostRow row) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gallery_delete_title)
                .setMessage(getString(R.string.gallery_delete_message, row.title))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.row_delete, (dialog, which) -> model.delete(row.id,
                        new CloudGallery.Callback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean value) {
                                snack(getString(R.string.gallery_deleted));
                            }

                            @Override
                            public void onFailure(@NonNull String message) {
                                snack(message);
                            }
                        }))
                .show();
    }

    // ---- publish one of my graphs ----

    private void choosePublication() {
        container.executors().disk(() -> container.repository().listGraphs(), new AppExecutors.Callback<List<GraphSummary>>() {
            @Override
            public void onSuccess(List<GraphSummary> graphs) {
                if (graphs.isEmpty()) {
                    snack(getString(R.string.gallery_nothing_to_publish));
                    return;
                }
                CharSequence[] names = new CharSequence[graphs.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = graphs.get(i).name;
                }
                new MaterialAlertDialogBuilder(GalleryActivity.this)
                        .setTitle(R.string.gallery_publish_choose)
                        .setItems(names, (dialog, which) -> confirmPublication(graphs.get(which)))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.gallery_publish_failed, error.getMessage()));
            }
        });
    }

    private void confirmPublication(@NonNull GraphSummary graph) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gallery_publish_confirm_title)
                .setMessage(getString(R.string.gallery_publish_confirm_message, graph.name, CloudPrefs.displayName(this)))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.gallery_publish_confirm_ok, (dialog, which) -> publish(graph))
                .show();
    }

    private void publish(@NonNull GraphSummary graph) {
        final Map<String, Object> fields;
        try {
            GraphDocument document = GraphFiles.read(graph.json);
            fields = GalleryPost.toFields(graph.name, CloudPrefs.displayName(this), model.uid(), document,
                    System.currentTimeMillis());
        } catch (GraphFormatException | IllegalArgumentException e) {
            snack(getString(R.string.gallery_publish_failed, e.getMessage()));
            return;
        }
        model.publish(fields, new CloudGallery.Callback<String>() {
            @Override
            public void onSuccess(String id) {
                snack(getString(R.string.gallery_published, graph.name));
            }

            @Override
            public void onFailure(@NonNull String message) {
                snack(getString(R.string.gallery_publish_failed, message));
            }
        });
    }

    private void snack(@NonNull String text) {
        Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG).show();
    }
}
