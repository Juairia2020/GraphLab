package com.graphlab.data;

import androidx.annotation.NonNull;
import com.graphlab.data.db.GraphRepository;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.GraphNames;
import java.util.List;

/** Adds a graph that came from outside (the sample library, the cloud gallery) to the user's saved graphs. */
public final class GraphImporter {
    private GraphImporter() {
    }

    /** Saves {@code doc} under a name that is not taken yet. The callback gets the name it was saved under. */
    public static void add(@NonNull AppContainer container, @NonNull GraphDocument doc, @NonNull String wantedName,
                           @NonNull AppExecutors.Callback<String> callback) {
        final GraphRepository repository = container.repository();
        container.executors().disk(() -> {
            String cleaned = GraphNames.clean(wantedName);
            List<String> taken = repository.graphNames();
            String name = GraphNames.unique(cleaned.isEmpty() ? GraphNames.UNTITLED : cleaned, taken);
            GraphDocument named = doc.withName(name);
            repository.insertGraph(name, GraphJson.write(named), doc.graph().nodeCount(), doc.graph().edgeCount(),
                    System.currentTimeMillis());
            return name;
        }, callback);
    }
}
