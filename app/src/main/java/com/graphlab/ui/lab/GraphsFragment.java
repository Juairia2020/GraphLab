package com.graphlab.ui.lab;

import com.graphlab.ui.common.OverflowMenu;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.db.GraphRecord;
import com.graphlab.data.db.GraphRepository;
import com.graphlab.data.db.GraphSummary;
import com.graphlab.databinding.FragmentGraphsBinding;
import com.google.android.material.chip.Chip;
import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFiles;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.GraphNames;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.ui.online.GalleryActivity;
import com.graphlab.ui.online.SampleLibraryActivity;
import com.graphlab.ui.common.GraphShare;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lab, Graphs mode: the library. It lists saved graphs (newest first), opens one in Build,
 * starts a new graph, imports a JSON or GraphML file, and offers rename, duplicate, share and
 * delete on every row. Delete is a swipe or a menu item and can be undone from the snackbar.
 *
 * <p>All database work goes through {@link AppExecutors#disk}; this fragment only reacts to the
 * results on the main thread.
 */
public class GraphsFragment extends Fragment implements GraphRowAdapter.Listener {
    /** A picked file larger than this is refused before it is read into memory. */
    private static final int MAX_IMPORT_BYTES = 2_000_000;

    private FragmentGraphsBinding binding;
    private LabViewModel model;
    private AppContainer container;
    private GraphRowAdapter adapter;
    @Nullable
    private Dialog openDialog;

    // Registered before the fragment starts, as the Activity Result API requires.
    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGraphsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
        container = GraphLabApp.container(requireContext());
        model.attach(container);
        OverflowMenu.attach(binding.graphsMore, binding.graphsImport, binding.graphsType, binding.graphsPhoto,
                binding.graphsSamples, binding.graphsCloud);

        adapter = new GraphRowAdapter(this);
        binding.graphsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.graphsList.setAdapter(adapter);
        binding.graphsList.setItemAnimator(null);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder holder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    deleteGraph(adapter.graphAt(position));
                }
            }
        }).attachToRecyclerView(binding.graphsList);

        binding.graphsNew.setOnClickListener(v -> {
            model.newGraph();
            ((LabHost) requireActivity()).showMode(LabMode.BUILD);
        });
        binding.graphsImport.setOnClickListener(v -> importLauncher.launch(new String[] {"*/*"}));
        binding.graphsType.setOnClickListener(v -> openDialog = EdgeListDialog.show(requireContext(), this::onTypedGraph));
        binding.graphsPhoto.setOnClickListener(v -> startActivity(new Intent(requireContext(), com.graphlab.ui.imports.ImportActivity.class)));
        binding.graphsAddSample.setOnClickListener(v -> addSample());
        // Explicit intents to two ordinary screens: ready-made graphs from the web, and graphs other users shared.
        binding.graphsSamples.setOnClickListener(v -> startActivity(new Intent(requireContext(), SampleLibraryActivity.class)));
        binding.graphsCloud.setOnClickListener(v -> startActivity(new Intent(requireContext(), GalleryActivity.class)));
        setUpCategories();

        reload();
    }

    @Override
    public void onDestroyView() {
        if (openDialog != null && openDialog.isShowing()) {
            openDialog.dismiss();
        }
        openDialog = null;
        binding = null;
        super.onDestroyView();
    }

    // ---- algorithm families ------------------------------------------------------------

    /** A chip per family. Tapping one lists its algorithms; picking one opens Run with it chosen. */
    private void setUpCategories() {
        binding.graphsCategories.removeAllViews();
        for (AlgorithmCategory category : Algorithms.categories()) {
            Chip chip = new Chip(requireContext());
            chip.setText(category.title());
            chip.setCheckable(false);
            chip.setOnClickListener(v -> chooseAlgorithm(category));
            binding.graphsCategories.addView(chip);
        }
    }

    private void chooseAlgorithm(@NonNull AlgorithmCategory category) {
        List<Algorithm> algorithms = Algorithms.inCategory(category);
        String[] names = new String[algorithms.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = algorithms.get(i).name();
        }
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(category.title())
                .setItems(names, (dialog, which) -> {
                    model.setAlgorithm(algorithms.get(which).id());
                    ((LabHost) requireActivity()).showMode(LabMode.RUN);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ---- list --------------------------------------------------------------------------

    /** Loads the list again. Queued behind any save already submitted, so a just-saved graph is in it. */
    private void reload() {
        final GraphRepository repository = container.repository();
        container.executors().disk(repository::listGraphs, new AppExecutors.Callback<List<GraphSummary>>() {
            @Override
            public void onSuccess(List<GraphSummary> graphs) {
                if (binding == null) {
                    return;
                }
                adapter.submitList(graphs);
                boolean empty = graphs.isEmpty();
                binding.graphsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.graphsList.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.graphsCount.setText(getResources().getQuantityString(R.plurals.graphs_count, graphs.size(), graphs.size()));
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.graphs_load_failed));
            }
        });
    }

    // ---- row actions -------------------------------------------------------------------

    @Override
    public void onOpen(@NonNull GraphSummary graph) {
        model.openGraph(graph.id, new LabViewModel.OpenListener() {
            @Override
            public void onOpened() {
                if (isAdded()) {
                    ((LabHost) requireActivity()).showMode(LabMode.BUILD);
                }
            }

            @Override
            public void onFailed(@NonNull String message) {
                snack(getString(R.string.graph_open_failed, message));
            }
        });
    }

    @Override
    public void onMenu(@NonNull GraphSummary graph, @NonNull View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.graph_row);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.row_open) {
                onOpen(graph);
            } else if (id == R.id.row_rename) {
                renameGraph(graph);
            } else if (id == R.id.row_duplicate) {
                duplicateGraph(graph);
            } else if (id == R.id.row_share_json) {
                shareGraph(graph, GraphFiles.Format.JSON);
            } else if (id == R.id.row_share_graphml) {
                shareGraph(graph, GraphFiles.Format.GRAPHML);
            } else if (id == R.id.row_delete) {
                deleteGraph(graph);
            }
            return true;
        });
        menu.show();
    }

    private void renameGraph(@NonNull GraphSummary graph) {
        openDialog = NameDialog.show(requireContext(), R.string.rename_title, graph.name, name -> {
            final long now = System.currentTimeMillis();
            container.executors().disk(() -> container.repository().renameGraph(graph.id, name, now),
                    new AppExecutors.Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean changed) {
                            model.renamedInLibrary(graph.id, name);
                            reload();
                        }

                        @Override
                        public void onFailure(@NonNull Exception error) {
                            snack(getString(R.string.rename_failed));
                        }
                    });
        });
    }

    private void duplicateGraph(@NonNull GraphSummary graph) {
        final GraphRepository repository = container.repository();
        container.executors().disk(() -> {
            List<String> names = repository.graphNames();
            String copy = GraphNames.copyOf(graph.name, names);
            return repository.insertGraph(copy, graph.json, graph.nodeCount, graph.edgeCount, System.currentTimeMillis());
        }, new AppExecutors.Callback<Long>() {
            @Override
            public void onSuccess(Long id) {
                reload();
                snack(getString(R.string.graph_duplicated));
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.duplicate_failed));
            }
        });
    }

    private void shareGraph(@NonNull GraphSummary graph, @NonNull GraphFiles.Format format) {
        container.executors().disk(() -> {
            GraphRecord record = container.repository().getGraph(graph.id);
            if (record == null) {
                throw new IllegalStateException("gone");
            }
            return GraphJson.read(record.json).withName(record.name);
        }, new AppExecutors.Callback<GraphDocument>() {
            @Override
            public void onSuccess(GraphDocument doc) {
                if (isAdded()) {
                    GraphShare.share(requireActivity(), container.executors(), doc, format);
                }
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.share_failed));
            }
        });
    }

    /** Hides the graph and offers Undo; it is removed for good when the bar goes away without Undo. */
    private void deleteGraph(@NonNull GraphSummary graph) {
        model.discardWorkingIf(graph.id);
        final GraphRepository repository = container.repository();
        container.executors().disk(() -> repository.setDeleted(graph.id, true, System.currentTimeMillis()),
                new AppExecutors.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean hidden) {
                        reload();
                        if (binding == null) {
                            container.executors().disk(() -> repository.purge(graph.id));
                            return;
                        }
                        Snackbar.make(binding.getRoot(), getString(R.string.graph_deleted, graph.name), Snackbar.LENGTH_LONG)
                                .setAction(R.string.action_undo, v ->
                                        container.executors().disk(() -> repository.setDeleted(graph.id, false, System.currentTimeMillis()),
                                                new AppExecutors.Callback<Boolean>() {
                                                    @Override
                                                    public void onSuccess(Boolean restored) {
                                                        reload();
                                                    }

                                                    @Override
                                                    public void onFailure(@NonNull Exception error) {
                                                    }
                                                }))
                                .addCallback(new Snackbar.Callback() {
                                    @Override
                                    public void onDismissed(Snackbar bar, int event) {
                                        if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                                            container.executors().disk(() -> repository.purge(graph.id));
                                        }
                                    }
                                })
                                .show();
                    }

                    @Override
                    public void onFailure(@NonNull Exception error) {
                        reload();
                        snack(getString(R.string.delete_failed));
                    }
                });
    }

    private void addSample() {
        final GraphRepository repository = container.repository();
        final String sampleName = getString(R.string.sample_graph_name);   // read on the main thread
        container.executors().disk(() -> {
            GraphEditor editor = GraphEditor.from(SampleGraph.sixNodeUndirected(), true);
            editor.scalePositions(3.0);   // the same spacing as the Sample button in Build
            String name = GraphNames.unique(sampleName, repository.graphNames());
            String json = GraphJson.write(new GraphDocument(name, true, editor.toStoredGraph()));
            return repository.insertGraph(name, json, editor.nodeCount(), editor.edgeCount(), System.currentTimeMillis());
        }, new AppExecutors.Callback<Long>() {
            @Override
            public void onSuccess(Long id) {
                reload();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.duplicate_failed));
            }
        });
    }

    // ---- import ------------------------------------------------------------------------

    /** The result of an import: which new row, and what to tell the user. */
    private static final class Imported {
        final long id;
        final String name;
        final int nodes;
        final int edges;

        Imported(long id, String name, int nodes, int edges) {
            this.id = id;
            this.name = name;
            this.nodes = nodes;
            this.edges = edges;
        }
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null || binding == null) {
            return;   // the user backed out of the picker
        }
        final ContentResolver resolver = requireContext().getContentResolver();
        final GraphRepository repository = container.repository();
        container.executors().disk(() -> {
            String text = readText(resolver, uri);
            GraphDocument doc = GraphFiles.read(text);
            String name = doc.name().equals(GraphNames.IMPORTED)
                    ? GraphFiles.nameFromFile(displayName(resolver, uri)) : doc.name();
            name = GraphNames.unique(name, repository.graphNames());
            GraphDocument named = doc.withName(name);
            long id = repository.insertGraph(name, GraphJson.write(named), doc.graph().nodeCount(),
                    doc.graph().edgeCount(), System.currentTimeMillis());
            return new Imported(id, name, doc.graph().nodeCount(), doc.graph().edgeCount());
        }, importCallback());
    }

    /** A graph typed in the dialog: saved under a name that is not taken yet, then offered in Build. */
    private void onTypedGraph(@NonNull GraphDocument document, @NonNull String wantedName) {
        final GraphRepository repository = container.repository();
        container.executors().disk(() -> {
            String name = GraphNames.unique(wantedName, repository.graphNames());
            GraphDocument named = document.withName(name);
            long id = repository.insertGraph(name, GraphJson.write(named), document.graph().nodeCount(),
                    document.graph().edgeCount(), System.currentTimeMillis());
            return new Imported(id, name, document.graph().nodeCount(), document.graph().edgeCount());
        }, importCallback());
    }

    /** What happens after an import (file or typed text): refresh the list and offer to open the new graph. */
    private AppExecutors.Callback<Imported> importCallback() {
        return new AppExecutors.Callback<Imported>() {
            @Override
            public void onSuccess(Imported result) {
                reload();
                if (binding == null) {
                    return;
                }
                String text = getString(R.string.import_done, result.name,
                        getResources().getQuantityString(R.plurals.nodes, result.nodes, result.nodes),
                        getResources().getQuantityString(R.plurals.edges, result.edges, result.edges));
                Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG)
                        .setAction(R.string.row_open, v -> model.openGraph(result.id, new LabViewModel.OpenListener() {
                            @Override
                            public void onOpened() {
                                if (isAdded()) {
                                    ((LabHost) requireActivity()).showMode(LabMode.BUILD);
                                }
                            }

                            @Override
                            public void onFailed(@NonNull String message) {
                                snack(getString(R.string.graph_open_failed, message));
                            }
                        }))
                        .show();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                String reason = error instanceof GraphFormatException ? error.getMessage()
                        : getString(R.string.import_unreadable);
                if (binding != null) {
                    Snackbar.make(binding.getRoot(), getString(R.string.import_failed, reason), Snackbar.LENGTH_LONG).show();
                }
            }
        };
    }

    /** Reads the whole file as UTF-8 text, refusing anything over {@link #MAX_IMPORT_BYTES}. */
    private static String readText(ContentResolver resolver, Uri uri) throws IOException, GraphFormatException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Cannot open the file");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > MAX_IMPORT_BYTES) {
                    throw new GraphFormatException("This file is too large to be a graph (over 2 MB).");
                }
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Nullable
    private static String displayName(ContentResolver resolver, Uri uri) {
        try (android.database.Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // Some providers refuse the query; the file name is only a convenience.
        }
        return uri.getLastPathSegment();
    }

    private void snack(@NonNull String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}
