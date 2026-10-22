package com.graphlab.ui.lab;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.db.GraphRecord;
import com.graphlab.data.db.SavedGraph;
import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.compare.Comparison;
import com.graphlab.engine.compare.Race;
import com.graphlab.engine.edit.EditHistory;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.edit.Viewport;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.InputParser;
import com.graphlab.engine.io.Background;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.GraphNames;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.play.Playback;
import com.graphlab.service.PlaybackService;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Trace;
import com.graphlab.ui.common.EditorTool;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Everything the Lab must not lose when the phone rotates: the graph being edited, the camera,
 * the active tool, the mode, and the current run with its play head. The Activity and the
 * fragments are rebuilt on rotation; this object is not.
 *
 * <p>Algorithms run on a background thread ({@link ExecutorService}); the result comes back on
 * the main thread through a Handler. Playback is driven by a main-thread Handler loop that lives
 * here, so a rotation does not stop it. At most one {@link RunListener} (the visible Run
 * fragment) is told about changes; with none attached the state is simply kept for next time.
 *
 * <p>Since MS4 the graph being edited ("the working graph") is linked to a row in the library:
 * {@link #graphId()} is 0 until it is first saved. A graph that has unsaved changes is saved
 * automatically when you leave Build, open another graph or stop the app, so work is never lost;
 * the Save button just does it now (and names a new graph). Saving happens on the disk executor,
 * and a save that finishes after you have opened another graph is recognised by its token and
 * ignored, so it can never overwrite the wrong graph's identity.
 */
public class LabViewModel extends ViewModel {

    /** What the Run screen listens to. All calls arrive on the main thread. */
    public interface RunListener {
        /** The selection, the running flag, the session or the error changed. */
        void onRunChanged();

        /** The step on screen changed. */
        void onPositionChanged();

        /** Play or pause (or the speed) changed. */
        void onPlayingChanged();
    }

    // ---- editor (MS2) ----
    private final GraphEditor editor = new GraphEditor(false, true);
    private final Viewport viewport = new Viewport();
    /** Undo and redo for Build (MS10). Reset whenever another graph is opened or the canvas is cleared by the library. */
    private final EditHistory history = new EditHistory(editor);
    private EditorTool tool = EditorTool.NODE;
    private LabMode mode = LabMode.GRAPHS;

    // ---- run (MS3) ----
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String algorithmId = "dijkstra";
    private int sourceNodeId = -1;
    @Nullable
    private RunSession session;
    private boolean running;
    @Nullable
    private String error;
    private int runToken;
    private double speed = Playback.DEFAULT_SPEED;
    private int panel;                       // 0 Steps, 1 State, 2 Insight
    private int targetNodeId = -1;
    private String arrayText = "38 27 43 3 9 82 10";
    private String itemsText = "1:1 3:4 4:5 5:7";
    private String capacityText = "7";
    // MS12: the dynamic-programming pack
    private String coinsText = "1 3 4";
    private String amountText = "6";
    private String firstText = "ABCBDAB";
    private String secondText = "BDCABA";
    private long runSeed = 1;
    @Nullable
    private RunListener listener;

    // ---- working graph and the library (MS4) ----
    private AppContainer container;
    private long graphId;                 // 0 = never saved
    @NonNull
    private String graphName = "";        // "" = no name yet (saved as "Untitled graph")
    private int savedVersion;             // editor.version() right after the last save or load
    private boolean nameDirty;            // a saved graph was renamed but not yet written
    private int workingToken;             // changes whenever another graph becomes the working one
    private boolean saving;
    private boolean saveAgain;
    @Nullable
    private SaveListener queuedListener;
    @Nullable
    private PendingRun pendingRun;        // a run finished before its graph had an id
    @Nullable
    private WorkingListener workingListener;

    /** What the Build header listens to: the name, the saved/unsaved state, the id. */
    public interface WorkingListener {
        void onWorkingChanged();
    }

    /** The answer to {@link #save}. Called on the main thread. */
    public interface SaveListener {
        void onSaved(@NonNull String name);

        void onFailed(@NonNull String message);
    }

    /** The answer to {@link #openGraph}. Called on the main thread. */
    public interface OpenListener {
        void onOpened();

        void onFailed(@NonNull String message);
    }

    private static final class PendingRun {
        final String algorithmId;
        final int steps;
        final String summary;

        PendingRun(String algorithmId, int steps, String summary) {
            this.algorithmId = algorithmId;
            this.steps = steps;
            this.summary = summary;
        }
    }

    private static final class Opened {
        final long id;
        final GraphDocument document;

        Opened(long id, GraphDocument document) {
            this.id = id;
            this.document = document;
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            RunSession current = session;
            if (current == null) {
                return;
            }
            boolean again = current.playback.tick();
            notifyPosition();
            if (again) {
                handler.postDelayed(this, current.playback.delayMillis());
            } else {
                notifyPlaying();
            }
        }
    };

    // ---- undo and redo (MS10) ----

    /** Call after each finished edit in Build; records an undo step if the graph changed. */
    public void checkpoint() {
        history.checkpoint(editor);
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    /** @return false if there was nothing to undo */
    public boolean undo() {
        boolean done = history.undo(editor);
        if (done) {
            editChangedByHistory();
        }
        return done;
    }

    /** @return false if there was nothing to redo */
    public boolean redo() {
        boolean done = history.redo(editor);
        if (done) {
            editChangedByHistory();
        }
        return done;
    }

    private void editChangedByHistory() {
        // A start or end node that the undo removed is forgotten; a run that no longer fits the graph is dropped
        // by refreshRunValidity() when Run appears, as after any other edit.
        if (sourceNodeId >= 0 && editor.findNode(sourceNodeId) == null) {
            sourceNodeId = -1;
        }
        if (targetNodeId >= 0 && editor.findNode(targetNodeId) == null) {
            targetNodeId = -1;
        }
        notifyWorking();
    }

    // ---- compare mode (MS10) ----

    private String compareA = "dijkstra";
    private String compareB = "bellman-ford";
    @Nullable
    private Comparison.Report compareReport;
    private int compareStructureVersion = -1;

    @NonNull
    public String compareA() {
        return compareA;
    }

    @NonNull
    public String compareB() {
        return compareB;
    }

    public void setCompareChoice(@NonNull String idA, @NonNull String idB) {
        if (idA.equals(compareA) && idB.equals(compareB)) {
            return;   // nothing changed (a spinner reports its old choice after a rotation): keep the answer
        }
        compareA = idA;
        compareB = idB;
        compareReport = null;
    }

    /** The last comparison, or null if none was made or the graph was edited since. */
    @Nullable
    public Comparison.Report compareReport() {
        return compareReport != null && compareStructureVersion == editor.structureVersion() ? compareReport : null;
    }

    /**
     * Runs the two chosen algorithms on the working graph.
     *
     * @throws IllegalArgumentException if the two ids are the same or unsuitable
     */
    public void runComparison(int source) {
        Graph graph = editor.toGraph();
        compareReport = Comparison.run(graph, compareA, compareB, source);
        compareStructureVersion = editor.structureVersion();
        raceGraph = graph;
        race = Race.of(compareReport);
        racePlayback = new Playback(race.length());
        handler.removeCallbacks(raceTick);
        notifyRace();
    }

    // ---- the race: both comparison traces played in step (MS10 S2) ----

    private Graph raceGraph;
    private Race race;
    private Playback racePlayback;
    private Runnable raceListener;

    private final Runnable raceTick = new Runnable() {
        @Override
        public void run() {
            Playback current = racePlayback;
            if (current == null) {
                return;
            }
            boolean again = current.tick();
            notifyRace();
            if (again) {
                handler.postDelayed(this, current.delayMillis());
            }
        }
    };

    /** The race of the last comparison, or null when there is none or the graph was edited since. */
    @Nullable
    public Race race() {
        return compareReport() == null ? null : race;
    }

    @Nullable
    public Playback racePlayback() {
        return compareReport() == null ? null : racePlayback;
    }

    /** The graph the comparison ran on, so frames can be laid out by it. */
    @Nullable
    public Graph raceGraph() {
        return compareReport() == null ? null : raceGraph;
    }

    /** One listener (the Compare screen) hears every change of the race position or play state. */
    public void setRaceListener(@Nullable Runnable listener) {
        raceListener = listener;
    }

    private void notifyRace() {
        if (raceListener != null) {
            raceListener.run();
        }
    }

    public void raceTogglePlay() {
        Playback current = racePlayback();
        if (current == null) {
            return;
        }
        handler.removeCallbacks(raceTick);
        current.togglePlay();
        if (current.isPlaying()) {
            handler.postDelayed(raceTick, current.delayMillis());
        }
        notifyRace();
    }

    public void raceStep(int delta) {
        Playback current = racePlayback();
        if (current == null) {
            return;
        }
        handler.removeCallbacks(raceTick);
        if (delta > 0) {
            current.next();
        } else if (delta < 0) {
            current.previous();
        } else {
            current.toFirst();
        }
        notifyRace();
    }

    public void raceSeek(int index) {
        Playback current = racePlayback();
        if (current == null) {
            return;
        }
        handler.removeCallbacks(raceTick);
        current.seek(index);
        notifyRace();
    }

    /** Pauses the race, for example when the Compare screen goes away. */
    public void racePause() {
        handler.removeCallbacks(raceTick);
        if (racePlayback != null) {
            racePlayback.pause();
        }
    }

    // ---- the photo behind the graph (MS10 S6) ----

    private android.graphics.Bitmap photo;
    private String photoFile;                 // the file name the bitmap was loaded from
    private int photoToken;                   // a newer load makes an older one's answer stale
    private final java.util.List<Runnable> photoListeners = new java.util.ArrayList<>();

    /** Hears every change of the photo or its settings (the Build and Run screens redraw). */
    public void addPhotoListener(@NonNull Runnable listener) {
        if (!photoListeners.contains(listener)) {
            photoListeners.add(listener);
        }
    }

    public void removePhotoListener(@NonNull Runnable listener) {
        photoListeners.remove(listener);
    }

    /** The picture behind the graph, or null when there is none or its file is missing. */
    @Nullable
    public android.graphics.Bitmap photo() {
        return photo;
    }

    /** The photo's settings in the working graph, or null for no photo. */
    @Nullable
    public Background background() {
        return editor.background();
    }

    private void notifyPhoto() {
        for (Runnable listener : new java.util.ArrayList<>(photoListeners)) {
            listener.run();
        }
    }

    /** Loads the bitmap that the working graph's background names (or drops it). Reads the file off the main thread. */
    private void reloadPhoto() {
        Background bg = editor.background();
        if (bg == null || container == null) {
            photo = null;
            photoFile = null;
            photoToken++;
            notifyPhoto();
            return;
        }
        if (bg.file().equals(photoFile) && photo != null) {
            notifyPhoto();
            return;
        }
        final int token = ++photoToken;
        final String file = bg.file();
        final AppContainer c = container;
        c.executors().disk(() -> c.backgrounds().load(file), new AppExecutors.Callback<android.graphics.Bitmap>() {
            @Override
            public void onSuccess(android.graphics.Bitmap bitmap) {
                if (token != photoToken) {
                    return;
                }
                photo = bitmap;
                photoFile = bitmap == null ? null : file;
                notifyPhoto();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                if (token == photoToken) {
                    photo = null;
                    photoFile = null;
                    notifyPhoto();
                }
            }
        });
    }

    /** The answer to {@link #setPhotoFrom}. Called on the main thread. */
    public interface PhotoListener {
        void onDone();

        void onFailed(@NonNull String message);
    }

    /**
     * Copies a picked photo into app storage (shrunk, as a JPEG) and makes it the photo behind the graph. A locked photo
     * is not replaced.
     */
    public void setPhotoFrom(@NonNull android.content.ContentResolver resolver, @NonNull android.net.Uri uri,
                             @NonNull PhotoListener listener) {
        final AppContainer c = container;
        if (c == null) {
            listener.onFailed("The library is not ready yet.");
            return;
        }
        Background current = editor.background();
        if (current != null && current.locked()) {
            listener.onFailed("The photo is locked. Unlock it first.");
            return;
        }
        final double opacity = current == null ? Background.DEFAULT_OPACITY : current.opacity();
        final int token = workingToken;
        c.executors().disk(() -> {
            String name = c.backgrounds().copyIn(resolver, uri);
            android.graphics.Bitmap bitmap = c.backgrounds().load(name);
            if (bitmap == null) {
                throw new java.io.IOException("The photo could not be read back.");
            }
            return new Object[] {name, bitmap};
        }, new AppExecutors.Callback<Object[]>() {
            @Override
            public void onSuccess(Object[] result) {
                if (token != workingToken) {
                    return;   // another graph was opened while the photo was being copied
                }
                String name = (String) result[0];
                photoToken++;
                photo = (android.graphics.Bitmap) result[1];
                photoFile = name;
                editor.setBackground(new Background(name, opacity, false));
                viewport.requestFit();
                notifyPhoto();
                notifyWorking();
                listener.onDone();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                listener.onFailed(error instanceof java.io.IOException && error.getMessage() != null
                        ? error.getMessage() : "The photo could not be added.");
            }
        });
    }

    /** Removes the photo (not when it is locked). Returns true if it was removed. */
    public boolean removePhoto() {
        Background bg = editor.background();
        if (bg == null || bg.locked()) {
            return false;
        }
        editor.setBackground(null);
        photoToken++;
        photo = null;
        photoFile = null;
        notifyPhoto();
        notifyWorking();
        return true;
    }

    /** Sets how see-through the photo is, 0 to 1 (not when it is locked). */
    public void setPhotoOpacity(double opacity) {
        Background bg = editor.background();
        if (bg == null || bg.locked()) {
            return;
        }
        editor.setBackground(bg.withOpacity(Math.max(0, Math.min(1, opacity))));
        notifyPhoto();
        notifyWorking();
    }

    public void setPhotoLocked(boolean locked) {
        Background bg = editor.background();
        if (bg == null) {
            return;
        }
        editor.setBackground(bg.withLocked(locked));
        notifyPhoto();
        notifyWorking();
    }

    /** Deletes photo files that no saved graph uses any more (a removed or replaced photo, a deleted graph). */
    private void sweepPhotos() {
        final AppContainer c = container;
        if (c == null) {
            return;
        }
        c.executors().disk(() -> {
            java.util.Set<String> inUse = new java.util.HashSet<>();
            for (com.graphlab.data.db.GraphSummary summary : c.repository().listGraphs()) {
                try {
                    Background bg = GraphJson.read(summary.json).background();
                    if (bg != null) {
                        inUse.add(bg.file());
                    }
                } catch (Exception unreadable) {
                    // A graph that cannot be read keeps no photo alive.
                }
            }
            Background working = editor.background();   // the open graph may not be saved yet
            if (working != null) {
                inUse.add(working.file());
            }
            c.backgrounds().sweep(inUse);
            return Boolean.TRUE;
        }, new AppExecutors.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean done) {
            }

            @Override
            public void onFailure(@NonNull Exception error) {
            }
        });
    }

    // ---- editor access ----

    @NonNull
    public GraphEditor editor() {
        return editor;
    }

    @NonNull
    public Viewport viewport() {
        return viewport;
    }

    @NonNull
    public EditorTool tool() {
        return tool;
    }

    public void setTool(@NonNull EditorTool tool) {
        this.tool = tool;
    }

    @NonNull
    public LabMode mode() {
        return mode;
    }

    public void setMode(@NonNull LabMode mode) {
        this.mode = mode;
    }

    /** Which tab of the Run screen is showing: false is Steps, true is State. Kept across rotation. */
    public int panel() {
        return panel;
    }

    public void setPanel(int panel) {
        this.panel = panel;
    }

    public int targetNodeId() {
        return targetNodeId;
    }

    /** Chooses the end node (the sink of a flow). A run with a different end is discarded. */
    public void setTarget(int nodeId) {
        if (nodeId == targetNodeId) {
            return;
        }
        targetNodeId = nodeId;
        if (session != null && session.algorithm.needsTarget()) {
            dropSession();
        }
        notifyRunChanged();
    }

    @NonNull
    public String arrayText() {
        return arrayText;
    }

    @NonNull
    public String itemsText() {
        return itemsText;
    }

    @NonNull
    public String capacityText() {
        return capacityText;
    }

    @NonNull
    public String coinsText() {
        return coinsText;
    }

    @NonNull
    public String amountText() {
        return amountText;
    }

    @NonNull
    public String firstText() {
        return firstText;
    }

    @NonNull
    public String secondText() {
        return secondText;
    }

    /** Remembers the coin problem's boxes (MS12). */
    public void setCoinTexts(@NonNull String coins, @NonNull String amount) {
        coinsText = coins;
        amountText = amount;
    }

    /** Remembers the two texts of LCS and edit distance (MS12). */
    public void setStringTexts(@NonNull String first, @NonNull String second) {
        firstText = first;
        secondText = second;
    }

    /** Remembers what was typed in the number or item boxes, so rotation and mode switches keep it. */
    public void setInputTexts(@NonNull String array, @NonNull String items, @NonNull String capacity) {
        arrayText = array;
        itemsText = items;
        capacityText = capacity;
    }

    // ---- run: selection ----

    public void setRunListener(@Nullable RunListener listener) {
        this.listener = listener;
    }

    /** Detaches the listener, but only if it is still the current one (a new view may already have replaced it). */
    public void clearRunListener(@NonNull RunListener old) {
        if (listener == old) {
            listener = null;
        }
    }

    @NonNull
    public Algorithm algorithm() {
        return Algorithms.byId(algorithmId);
    }

    /** Picks another algorithm. A run of the old one is discarded. */
    public void setAlgorithm(@NonNull String id) {
        if (id.equals(algorithmId)) {
            return;
        }
        Algorithms.byId(id);   // throws for an unknown id, before anything changes
        algorithmId = id;
        dropSession();
        notifyRunChanged();
    }

    /** The chosen start node, or -1 for none. */
    public int sourceNodeId() {
        return sourceNodeId;
    }

    /** Chooses where the algorithm starts. A run with a different start is discarded. */
    public void setSource(int nodeId) {
        if (nodeId == sourceNodeId) {
            return;
        }
        sourceNodeId = nodeId;
        if (session != null && session.algorithm.needsSource()) {
            dropSession();
        }
        notifyRunChanged();
    }

    /**
     * Call when the Run screen appears. Drops a run that no longer matches the graph (it was
     * edited in Build) and repairs the start node if it was deleted.
     *
     * @return true if a stale run was dropped, so the screen can say so
     */
    public boolean refreshRunValidity() {
        boolean dropped = false;
        if (session != null && session.dependsOnGraph() && session.structureVersion != editor.structureVersion()) {
            dropSession();
            dropped = true;
        }
        if (sourceNodeId < 0 || editor.findNode(sourceNodeId) == null) {
            sourceNodeId = editor.isEmpty() ? -1 : editor.nodeAt(0).id();
        }
        if (targetNodeId >= 0 && editor.findNode(targetNodeId) == null) {
            targetNodeId = -1;
        }
        if (targetNodeId < 0 && editor.nodeCount() > 1) {
            targetNodeId = editor.nodeAt(editor.nodeCount() - 1).id();   // a sensible default end: the last node
        }
        return dropped;
    }

    // ---- run: state ----

    @Nullable
    public RunSession session() {
        return session;
    }

    public boolean isRunning() {
        return running;
    }

    /** The message of a failed run, once. Returns null afterwards, so a rotation does not repeat it. */
    @Nullable
    public String consumeError() {
        String message = error;
        error = null;
        return message;
    }

    // ---- run: start ----

    /**
     * Runs the chosen algorithm on a snapshot of the current graph, on a background thread.
     * The caller has already checked that the graph has nodes and, if the algorithm needs one,
     * a start node.
     */
    public void startRun() {
        saveIfDirty();   // so the run can be recorded against a saved graph
        final Algorithm algorithm = algorithm();
        final Graph graph = editor.toGraph();
        final int version = editor.structureVersion();
        Params base = algorithm.needsSource() ? Params.from(sourceNodeId) : Params.none();
        if (algorithm.needsTarget()) {
            base = base.withTarget(targetNodeId);
        }
        final Params params = base.withSeed(runSeed++);   // a new seed each run: random pivots and contractions differ
        final Input input;
        try {
            switch (algorithm.inputKind()) {
                case ARRAY:
                    input = InputParser.parseArray(arrayText);
                    break;
                case KNAPSACK:
                    input = InputParser.parseKnapsack(itemsText, capacityText);
                    break;
                case COINS:
                    input = InputParser.parseCoins(coinsText, amountText);
                    break;
                case STRINGS:
                    input = InputParser.parseStrings(firstText, secondText);
                    break;
                default:
                    input = new GraphInput(graph);
                    break;
            }
        } catch (IllegalArgumentException e) {
            dropSession();
            error = e.getMessage();
            notifyRunChanged();
            return;
        }

        dropSession();
        error = null;
        running = true;
        final int myToken = ++runToken;
        notifyRunChanged();

        executor.execute(() -> {
            Trace trace = null;
            String failure = null;
            try {
                trace = algorithm.run(input, params);
            } catch (IllegalArgumentException e) {
                failure = e.getMessage();   // the engine's own explanation, for example "needs non-negative weights"
            } catch (RuntimeException e) {
                failure = e.toString();
            }
            final Trace finalTrace = trace;
            final String finalFailure = failure;
            handler.post(() -> finishRun(myToken, algorithm, graph, params, version, finalTrace, finalFailure));
        });
    }

    private void finishRun(int token, Algorithm algorithm, Graph graph, Params params, int version,
                           @Nullable Trace trace, @Nullable String failure) {
        if (token != runToken) {
            return;   // an older run finishing late; a newer one is in charge
        }
        running = false;
        if (trace != null) {
            session = new RunSession(algorithm, graph, params, trace, version);
            session.playback.setSpeed(speed);   // a new run keeps the speed you chose
            recordRun(algorithm, graph, params, trace);
        } else {
            error = failure == null ? "The algorithm could not run." : failure;
        }
        notifyRunChanged();
        notifyPosition();
        notifyPlaying();
    }

    // ---- run: playback ----

    public void togglePlay() {
        RunSession current = session;
        if (current == null) {
            return;
        }
        handler.removeCallbacks(tick);
        current.playback.togglePlay();
        if (current.playback.isPlaying()) {
            handler.postDelayed(tick, current.playback.delayMillis());
        }
        notifyPosition();
        notifyPlaying();
    }

    /** True while a playing run has been handed to {@link PlaybackService} because the app was left. */
    private boolean backgroundPlayback;

    /**
     * The app is being left (Home, another app, another screen). A run that is playing carries on in the background with
     * video-style controls in the notification; if that is not possible (notifications off) it pauses as before.
     */
    public void leaveScreen(@NonNull Context context) {
        final RunSession current = session;
        if (current == null || !current.playback.isPlaying()) {
            return;
        }
        handler.removeCallbacks(tick);
        if (PlaybackService.handOff(context, current.playback, current.algorithm.name(),
                index -> current.trace.step(index).message())) {
            backgroundPlayback = true;
        } else {
            current.playback.pause();
            notifyPlaying();
        }
    }

    /** The app is back: the service lets go and this screen's clock carries on from the step the service reached. */
    public void returnToScreen(@NonNull Context context) {
        if (!backgroundPlayback) {
            return;
        }
        backgroundPlayback = false;
        RunSession current = session;
        if (current == null) {
            return;
        }
        PlaybackService.takeBack(context, current.playback);
        handler.removeCallbacks(tick);
        if (current.playback.isPlaying()) {
            handler.postDelayed(tick, current.playback.delayMillis());
        }
        notifyPosition();
        notifyPlaying();
    }

    public void pausePlayback() {
        RunSession current = session;
        if (backgroundPlayback || current == null || !current.playback.isPlaying()) {
            return;   // in the background the service is in charge of the clock
        }
        handler.removeCallbacks(tick);
        current.playback.pause();
        notifyPlaying();
    }

    public void stepNext() {
        manual(session == null ? null : session.playback::next);
    }

    public void stepPrevious() {
        manual(session == null ? null : session.playback::previous);
    }

    public void toFirst() {
        manual(session == null ? null : session.playback::toFirst);
    }

    public void toLast() {
        manual(session == null ? null : session.playback::toLast);
    }

    public void seek(int step) {
        RunSession current = session;
        if (current != null) {
            manual(() -> current.playback.seek(step));
        }
    }

    /** The playback speed, 0.5 to 4. Kept between runs. */
    public double speed() {
        return speed;
    }

    public void setSpeed(double newSpeed) {
        RunSession current = session;
        if (current == null) {
            return;
        }
        current.playback.setSpeed(newSpeed);
        speed = current.playback.speed();
        if (current.playback.isPlaying()) {
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, current.playback.delayMillis());
        }
        notifyPlaying();
    }

    private interface Move {
        boolean apply();
    }

    private void manual(@Nullable Move move) {
        if (move == null) {
            return;
        }
        handler.removeCallbacks(tick);
        boolean wasPlaying = session != null && session.playback.isPlaying();
        boolean moved = move.apply();   // a manual move always pauses
        if (moved) {
            notifyPosition();
        }
        if (wasPlaying) {
            notifyPlaying();
        }
    }

    private void dropSession() {
        handler.removeCallbacks(tick);
        backgroundPlayback = false;
        session = null;
    }

    // ---- notifications ----

    private void notifyRunChanged() {
        if (listener != null) {
            listener.onRunChanged();
        }
    }

    private void notifyPosition() {
        if (listener != null) {
            listener.onPositionChanged();
        }
    }

    private void notifyPlaying() {
        if (listener != null) {
            listener.onPlayingChanged();
        }
    }

    // ---- the working graph and the library (MS4) ----

    private boolean initialized;

    /**
     * Returns whether this ViewModel had already been used, and marks it used. The Lab Activity asks
     * once per start: "false" together with a restored screen means the process was killed and
     * everything in memory is gone, so the Lab goes back to the library instead of an empty editor.
     */
    public boolean markInitialized() {
        boolean was = initialized;
        initialized = true;
        return was;
    }

    /** Gives the ViewModel the database and executors. Safe to call again on every screen start. */
    public void attach(@NonNull AppContainer container) {
        this.container = container;
    }

    public void setWorkingListener(@Nullable WorkingListener listener) {
        this.workingListener = listener;
    }

    public void clearWorkingListener(@NonNull WorkingListener old) {
        if (workingListener == old) {
            workingListener = null;
        }
    }

    /** The library id of the working graph, or 0 if it has never been saved. */
    public long graphId() {
        return graphId;
    }

    /** The working graph's name, or "" if it has none yet. */
    @NonNull
    public String graphName() {
        return graphName;
    }

    /** True when there is something that is not in the library yet. */
    public boolean isDirty() {
        return graphId == 0 ? (!editor.isEmpty() || editor.background() != null)
                : (editor.version() != savedVersion || nameDirty);
    }

    /** Starts a new empty graph. The graph that was open is saved first if it has changes. */
    public void newGraph() {
        saveIfDirty();   // serialises the current graph right now, before it is cleared
        beginWorking(0, "");
        editor.clear();
        editor.setBackground(null);
        history.reset(editor);
        viewport.requestFit();
        savedVersion = editor.version();
        reloadPhoto();
        notifyWorking();
    }

    /** Opens a saved graph in the editor. The graph that was open is saved first if it has changes. */
    public void openGraph(long id, @NonNull OpenListener listener) {
        if (container == null) {
            listener.onFailed("The library is not ready yet.");
            return;
        }
        saveIfDirty();
        final AppContainer c = container;
        c.executors().disk(() -> {
            GraphRecord record = c.repository().getGraph(id);
            if (record == null) {
                throw new IllegalStateException("This graph no longer exists.");
            }
            // The database name wins over the name inside the stored text.
            return new Opened(record.id, GraphJson.read(record.json).withName(record.name));
        }, new AppExecutors.Callback<Opened>() {
            @Override
            public void onSuccess(Opened opened) {
                applyDocument(opened.id, opened.document);
                listener.onOpened();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                listener.onFailed(error instanceof GraphFormatException || error instanceof IllegalStateException
                        ? error.getMessage() : "This graph could not be opened.");
            }
        });
    }

    /** Makes a document the working graph. Pass the library id it came from (0 if it is not saved). */
    public void applyDocument(long id, @NonNull GraphDocument document) {
        beginWorking(id, document.name());
        editor.load(document.graph(), document.weighted());
        editor.setBackground(document.background());
        history.reset(editor);
        viewport.requestFit();
        savedVersion = editor.version();
        reloadPhoto();
        notifyWorking();
    }

    /** The library deleted this graph: if it is the one being edited, start from an empty canvas without saving it back. */
    public void discardWorkingIf(long deletedId) {
        if (graphId != 0 && graphId == deletedId) {
            beginWorking(0, "");
            editor.clear();
            editor.setBackground(null);
            history.reset(editor);
            viewport.requestFit();
            savedVersion = editor.version();
            reloadPhoto();
            notifyWorking();
        }
    }

    /** The library renamed this graph: keep the working copy's name in step so a later autosave does not undo it. */
    public void renamedInLibrary(long id, @NonNull String name) {
        if (graphId != 0 && graphId == id) {
            graphName = name;
            nameDirty = false;
            notifyWorking();
        }
    }

    /** Renames the working graph. A saved graph (or one with content) is written straight away. */
    public void renameWorking(@NonNull String name) {
        graphName = name;
        if (graphId != 0) {
            nameDirty = true;
        }
        notifyWorking();
        if (graphId != 0 || !editor.isEmpty()) {
            save(null, null);
        }
    }

    /** Saves if there is anything unsaved. Called when leaving Build, switching graphs and stopping the app. */
    public void saveIfDirty() {
        if (isDirty()) {
            save(null, null);
        }
    }

    /**
     * Saves the working graph to the library now. {@code explicitName} names a graph that has none;
     * with null the name is kept (or "Untitled graph" is chosen for a first save). The listener, if
     * any, is told when the write is done.
     */
    public void save(@Nullable String explicitName, @Nullable SaveListener listener) {
        if (container == null) {
            if (listener != null) {
                listener.onFailed("The library is not ready yet.");
            }
            return;
        }
        if (explicitName != null) {
            graphName = explicitName;
            if (graphId != 0) {
                nameDirty = true;
            }
        }
        if (graphId == 0 && editor.isEmpty() && editor.background() == null && explicitName == null) {
            if (listener != null) {
                listener.onSaved(graphName);
            }
            return;   // nothing worth a library entry
        }
        if (saving) {
            saveAgain = true;
            if (listener != null) {
                queuedListener = listener;
            }
            return;
        }
        saving = true;
        final int token = workingToken;
        final int versionAtStart = editor.version();
        final String nameAtStart = graphName;
        final long idAtStart = graphId;
        // A saved graph keeps its stored name unless it was renamed; a new one uses the typed name, else an automatic one.
        final String nameToWrite = idAtStart == 0
                ? (nameAtStart.isEmpty() ? null : nameAtStart)
                : (nameDirty ? nameAtStart : null);
        final String json = GraphJson.write(new GraphDocument(
                nameAtStart.isEmpty() ? GraphNames.UNTITLED : nameAtStart, editor.isWeighted(), editor.toStoredGraph(),
                editor.background()));
        final int nodes = editor.nodeCount();
        final int edges = editor.edgeCount();
        final AppContainer c = container;
        c.executors().disk(() -> c.repository().saveGraph(idAtStart, nameToWrite, json, nodes, edges,
                System.currentTimeMillis()), new AppExecutors.Callback<SavedGraph>() {
            @Override
            public void onSuccess(SavedGraph saved) {
                saving = false;
                if (token == workingToken) {
                    graphId = saved.id;
                    if (graphName.equals(nameAtStart)) {   // not renamed again while saving
                        graphName = saved.name;
                        nameDirty = false;
                    }
                    savedVersion = versionAtStart;         // newer edits keep it dirty
                    flushPendingRun();
                    sweepPhotos();
                } else {
                    pendingRun = null;
                }
                notifyWorking();
                if (listener != null) {
                    listener.onSaved(saved.name);
                }
                runQueuedSave();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                saving = false;
                if (listener != null) {
                    listener.onFailed("The graph could not be saved.");
                }
                runQueuedSave();
            }
        });
    }

    private void runQueuedSave() {
        if (!saveAgain) {
            return;
        }
        saveAgain = false;
        SaveListener queued = queuedListener;
        queuedListener = null;
        if (queued != null) {
            save(null, queued);
        } else {
            saveIfDirty();
        }
    }

    private void beginWorking(long id, @NonNull String name) {
        workingToken++;
        graphId = id;
        graphName = name;
        nameDirty = false;
        pendingRun = null;
        sourceNodeId = -1;
        dropSession();
    }

    private void recordRun(@NonNull Algorithm algorithm, @NonNull Graph graph, @NonNull Params params, @NonNull Trace trace) {
        if (container == null) {
            return;
        }
        String summary = algorithm.name() + (algorithm.needsSource() ? " from " + graph.nodeName(params.source()) : "")
                + (algorithm.needsTarget() && params.hasTarget() ? " to " + graph.nodeName(params.target()) : "");
        PendingRun run = new PendingRun(algorithm.id(), trace.size(), summary);
        if (graphId == 0 && saving) {
            pendingRun = run;   // wait for the save to give the graph an id
        } else {
            writeRun(graphId, run);
        }
    }

    private void flushPendingRun() {
        if (pendingRun != null) {
            writeRun(graphId, pendingRun);
            pendingRun = null;
        }
    }

    private void writeRun(long id, @NonNull PendingRun run) {
        final AppContainer c = container;
        c.executors().disk(() -> c.repository().addRun(id, run.algorithmId, run.steps, run.summary,
                System.currentTimeMillis()));
    }

    private void notifyWorking() {
        if (workingListener != null) {
            workingListener.onWorkingChanged();
        }
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
