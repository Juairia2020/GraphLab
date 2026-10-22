package com.graphlab.ui.toolkit;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.cloud.RoutesProxyClient;
import com.graphlab.data.db.SqliteMatrixCache;
import com.graphlab.databinding.ContentToolkitBinding;
import com.graphlab.ui.common.BaseActivity;
import com.graphlab.ui.common.Tab;
import com.graphlab.engine.edit.WeightFormat;
import com.graphlab.engine.geo.Cities;
import com.graphlab.engine.geo.City;
import com.graphlab.engine.geo.Geo;
import com.graphlab.engine.geo.MapFlow;
import com.graphlab.engine.geo.MapTools;
import com.graphlab.engine.geo.RouteMatrix;
import com.graphlab.engine.geo.RouteService;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.State;
import com.graphlab.ui.common.StateColors;
import com.graphlab.ui.lab.LabActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

/**
 * The Toolkit on a free OpenStreetMap map (MS14, no key and no billing: osmdroid). The person taps the map to drop up to 10 places (or uses the Places list) and picks one tool. One road-distance matrix is asked for
 * (cache first, then the proxy; if neither works, a marked straight-line estimate), the engine solves it, and the answer is
 * drawn on the map and written on the result sheet. "Replay in Lab" saves the same graph and opens it in Run mode.
 *
 * <p>Two scenarios sit on the same screen (MS20): <b>Flood relief</b> and <b>City bus network</b>. They have no fixed data: the
 * person picks the area (a centre place and how many places around it) or taps the places, and the engine runs on whatever was
 * chosen. In flood relief the first place is the depot and the last is the flooded area; in the bus network the first and
 * last places are the two ends of the line to find.
 */
public class MapActivity extends BaseActivity {
    private static final String KEY_PLACES = "places";
    private static final String KEY_TOOL = "tool";
    /** A tap this close to a bundled city picks that city instead of a new pin. */
    private static final double SNAP_KM = 8.0;

    private enum Tool { ROUTE, TRIP, NETWORK, FLOW, FLOOD, BUS }

    /** What a finished run hands back to the screen. */
    private static final class Outcome {
        Graph graph;
        String graphName;
        List<City> places = new ArrayList<>();
        List<double[]> lines = new ArrayList<>();      // {fromIndex, toIndex, style} style 0 idle, 1 answer, 2 cut, 3 second tour
        List<String> markerTitles = new ArrayList<>();
        String sheet;
    }

    private ContentToolkitBinding binding;
    private MapView map;
    private MapEventsOverlay tapOverlay;
    private final ArrayList<City> picked = new ArrayList<>();
    private Tool tool = Tool.ROUTE;
    private Outcome shown;
    private boolean working;

    @Override
    protected Tab getTab() {
        return Tab.TOOLKIT;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.content_toolkit;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // osmdroid: a user agent is required by the OpenStreetMap tile rules; the tile cache lives in the app's own cache folder,
        // so no storage permission is needed. This must be set before the MapView is created (the base class inflates it).
        org.osmdroid.config.IConfigurationProvider osm = org.osmdroid.config.Configuration.getInstance();
        osm.load(getApplicationContext(), getSharedPreferences("osmdroid", MODE_PRIVATE));
        osm.setUserAgentValue(getPackageName());
        File osmBase = new File(getCacheDir(), "osmdroid");
        osm.setOsmdroidBasePath(osmBase);
        osm.setOsmdroidTileCache(new File(osmBase, "tiles"));
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onContentInflated(android.view.View content, @Nullable Bundle savedInstanceState) {
        binding = ContentToolkitBinding.bind(content);
        if (savedInstanceState != null) {
            ArrayList<String> saved = savedInstanceState.getStringArrayList(KEY_PLACES);
            if (saved != null) {
                for (String line : saved) {
                    String[] f = line.split("\t");
                    if (f.length == 3) {
                        try {
                            picked.add(new City(f[0], Double.parseDouble(f[1]), Double.parseDouble(f[2])));
                        } catch (IllegalArgumentException ignored) {
                            // a damaged line is dropped
                        }
                    }
                }
            }
            tool = Tool.values()[Math.min(savedInstanceState.getInt(KEY_TOOL), Tool.values().length - 1)];
        }
        applyTool();
        binding.mapToolButton.setOnClickListener(v -> chooseTool());
        binding.mapPick.setOnClickListener(v -> pickPlaces());
        binding.mapArea.setOnClickListener(v -> pickArea());
        binding.mapClear.setOnClickListener(v -> {
            picked.clear();
            picksChanged();
        });
        binding.mapRun.setOnClickListener(v -> run());
        binding.mapReplay.setOnClickListener(v -> replay());
        showPlaces();
        setupMap();
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        map.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        map.onDetach();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> lines = new ArrayList<>();
        for (City c : picked) {
            lines.add(c.name() + "\t" + c.lat() + "\t" + c.lon());
        }
        outState.putStringArrayList(KEY_PLACES, lines);
        outState.putInt(KEY_TOOL, tool.ordinal());
    }

    private void setupMap() {
        map = binding.map;
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(3.0);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            map.getOverlayManager().getTilesOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        }
        map.getController().setZoom(6.5);
        map.getController().setCenter(new GeoPoint(23.7, 90.0));
        tapOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                addPin(point);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        });
        if (shown != null) {
            draw(shown);
        } else {
            drawPicks();
        }
    }

    /** Clears everything drawn on the map, keeping only the overlay that turns a tap into a pin. */
    private void resetOverlays() {
        map.getOverlays().clear();
        map.getOverlays().add(tapOverlay);
    }

    /** A round numbered pin: green for the first place, blue for the rest. */
    private Marker pin(GeoPoint at, int number, boolean first, String title) {
        float dp = getResources().getDisplayMetrics().density;
        int size = Math.round(30 * dp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(first ? 0xFF2E7D32 : 0xFF1565C0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1.5f * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * dp);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1.5f * dp, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(13 * dp);
        canvas.drawText(Integer.toString(number), size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
        Marker marker = new Marker(map);
        marker.setPosition(at);
        marker.setIcon(new BitmapDrawable(getResources(), bitmap));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle(title);
        return marker;
    }

    /** The top bar's tool menu: every Toolkit action is on this one screen. */
    private void chooseTool() {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, binding.mapToolButton);
        for (Tool t : Tool.values()) {
            menu.getMenu().add(0, t.ordinal(), t.ordinal(), toolName(t));
        }
        menu.setOnMenuItemClickListener(item -> {
            tool = Tool.values()[item.getItemId()];
            applyTool();
            picksChanged();
            return true;
        });
        menu.show();
    }

    private String toolName(Tool t) {
        switch (t) {
            case TRIP:
                return getString(R.string.tool_name_trip);
            case NETWORK:
                return getString(R.string.tool_name_network);
            case FLOW:
                return getString(R.string.tool_name_flow);
            case FLOOD:
                return getString(R.string.scenario_flood_title);
            case BUS:
                return getString(R.string.scenario_bus_title);
            default:
                return getString(R.string.tool_name_route);
        }
    }

    /** Shows the chosen tool in the top bar and, for a scenario, what the first and last place mean. */
    private void applyTool() {
        binding.mapToolButton.setText(getString(R.string.tool_button, toolName(tool)));
        boolean scenario = tool == Tool.FLOOD || tool == Tool.BUS;
        binding.mapScenarioHint.setVisibility(scenario ? android.view.View.VISIBLE : android.view.View.GONE);
        if (scenario) {
            binding.mapScenarioHint.setText(tool == Tool.FLOOD ? R.string.scenario_flood_hint : R.string.scenario_bus_hint);
        }
        binding.mapSheet.setText(emptySheet());
        showPlaces();
    }

    /** What the result sheet says before a run: the general help, or what the first and last place mean in a scenario. */
    private String emptySheet() {
        switch (tool) {
            case FLOOD:
                return getString(R.string.scenario_flood_empty);
            case BUS:
                return getString(R.string.scenario_bus_empty);
            default:
                return getString(R.string.map_sheet_empty);
        }
    }

    /** A tap on the map drops a place there, or picks the bundled city close to it. */
    private void addPin(@NonNull GeoPoint at) {
        if (working) {
            return;
        }
        if (picked.size() >= RouteMatrix.MAX_PLACES) {
            Toast.makeText(this, getString(R.string.map_pick_limit, RouteMatrix.MAX_PLACES), Toast.LENGTH_SHORT).show();
            return;
        }
        City chosen = null;
        double best = SNAP_KM;
        for (City c : Cities.all()) {
            double d = Geo.haversineKm(c.lat(), c.lon(), at.getLatitude(), at.getLongitude());
            if (d < best) {
                best = d;
                chosen = c;
            }
        }
        if (chosen == null) {
            // The name carries the position, because the name is the identity in the distance cache.
            chosen = new City(String.format(Locale.US, "Pin %.3f, %.3f", at.getLatitude(), at.getLongitude()), at.getLatitude(), at.getLongitude());
        }
        if (picked.contains(chosen)) {
            Toast.makeText(this, getString(R.string.map_already_picked, chosen.name()), Toast.LENGTH_SHORT).show();
            return;
        }
        picked.add(chosen);
        picksChanged();
    }

    /** Tapping a marker takes that place off the list. */
    private void removePin(@NonNull Marker marker) {
        Object tag = marker.getRelatedObject();
        if (tag instanceof City && !working) {
            picked.remove(tag);
            picksChanged();
        }
    }

    /** The list changed: the old answer no longer matches, so it is cleared and the pins are redrawn. */
    private void picksChanged() {
        shown = null;
        binding.mapReplay.setEnabled(false);
        binding.mapSheet.setText(emptySheet());
        showPlaces();
        drawPicks();
    }

    private void drawPicks() {
        if (map == null) {
            return;
        }
        resetOverlays();
        for (int i = 0; i < picked.size(); i++) {
            City c = picked.get(i);
            Marker m = pin(new GeoPoint(c.lat(), c.lon()), i + 1, i == 0, getString(R.string.map_place_line, i + 1, c.name()));
            m.setRelatedObject(c);
            m.setSubDescription(getString(R.string.map_pin_remove));
            m.setOnMarkerClickListener((marker, view) -> {
                removePin(marker);
                return true;
            });
            map.getOverlays().add(m);
        }
        map.invalidate();
    }

    private void showPlaces() {
        if (picked.isEmpty()) {
            binding.mapPlaces.setText(R.string.map_places_none);
        } else {
            List<String> names = new ArrayList<>();
            for (City c : picked) {
                names.add(c.name());
            }
            binding.mapPlaces.setText(getString(R.string.map_places_count, picked.size(), String.join(", ", names)));
        }
        binding.mapClear.setEnabled(!picked.isEmpty());
    }

    private static final int[] AREA_SIZES = {4, 6, 8, 10};

    /**
     * The person chooses the area: first a centre place, then how many places to take (the centre and the closest ones). The
     * places replace the list. Nothing is fixed: another centre gives another area. The first place is the centre and the last
     * is the farthest in the area, which is what the scenarios use as depot and flooded area, or as the two ends of the bus line.
     */
    private void pickArea() {
        if (working) {
            return;
        }
        final List<City> bundled = new ArrayList<>(Cities.all());
        bundled.sort((x, y) -> x.name().compareTo(y.name()));
        final String[] names = new String[bundled.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = bundled.get(i).name();
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.map_area_centre)
                .setItems(names, (dialog, which) -> chooseAreaSize(bundled.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void chooseAreaSize(@NonNull final City centre) {
        final String[] labels = new String[AREA_SIZES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(R.string.map_area_size, AREA_SIZES[i]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.map_area_around, centre.name()))
                .setItems(labels, (dialog, which) -> {
                    picked.clear();
                    picked.addAll(Cities.area(centre, Math.min(AREA_SIZES[which], RouteMatrix.MAX_PLACES)));
                    picksChanged();
                    if (map != null) {
                        List<GeoPoint> points = new ArrayList<>();
                        for (City c : picked) {
                            points.add(new GeoPoint(c.lat(), c.lon()));
                        }
                        final BoundingBox box = BoundingBox.fromGeoPoints(points);
                        final int margin = Math.round(56 * getResources().getDisplayMetrics().density);
                        map.post(() -> map.zoomToBoundingBox(box, true, margin));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void pickPlaces() {
        final List<City> all = new ArrayList<>();
        for (City c : picked) {
            if (Cities.byName(c.name()) == null) {
                all.add(c);                      // pins dropped on the map stay in the list
            }
        }
        List<City> bundled = new ArrayList<>(Cities.all());
        bundled.sort((x, y) -> x.name().compareTo(y.name()));
        all.addAll(bundled);
        final String[] names = new String[all.size()];
        final boolean[] on = new boolean[all.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = all.get(i).name();
            on[i] = picked.contains(all.get(i));
        }
        final ArrayList<City> chosen = new ArrayList<>(picked);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.map_pick_title, RouteMatrix.MAX_PLACES))
                .setMultiChoiceItems(names, on, (dialog, which, checked) -> {
                    if (checked) {
                        if (chosen.size() >= RouteMatrix.MAX_PLACES) {
                            on[which] = false;
                            ((androidx.appcompat.app.AlertDialog) dialog).getListView().setItemChecked(which, false);
                            Toast.makeText(this, getString(R.string.map_pick_limit, RouteMatrix.MAX_PLACES), Toast.LENGTH_SHORT).show();
                        } else {
                            chosen.add(all.get(which));
                        }
                    } else {
                        chosen.remove(all.get(which));
                    }
                })
                .setPositiveButton(R.string.map_pick_done, (dialog, which) -> {
                    picked.clear();
                    picked.addAll(chosen);
                    picksChanged();
                })
                .show();
    }

    private void run() {
        if (working) {
            return;
        }
        final Tool chosen = tool;
        if (picked.size() < 2) {
            binding.mapSheet.setText(R.string.map_need_two);
            return;
        }
        if (chosen != Tool.ROUTE && picked.size() < 3) {
            binding.mapSheet.setText(R.string.map_need_three);
            return;
        }
        final List<City> places = new ArrayList<>(picked);
        working = true;
        binding.mapRun.setEnabled(false);
        binding.mapSheet.setText(R.string.map_working);
        final AppContainer container = GraphLabApp.container(this);
        container.executors().disk(() -> solve(container, chosen, places), new AppExecutors.Callback<Outcome>() {
            @Override
            public void onSuccess(Outcome result) {
                working = false;
                binding.mapRun.setEnabled(true);
                shown = result;
                binding.mapSheet.setText(result.sheet);
                binding.mapReplay.setEnabled(true);
                draw(result);
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                working = false;
                binding.mapRun.setEnabled(true);
                binding.mapSheet.setText(getString(R.string.map_failed, String.valueOf(error.getMessage())));
            }
        });
    }

    /** Off the main thread: the matrix (cache, then proxy), then the engine. Nothing here touches a View. */
    private Outcome solve(AppContainer container, Tool chosen, List<City> places) {
        Outcome out = new Outcome();
        // Road distances: the Google proxy first when it is set up, then the free OSRM service, then the estimate.
        List<String> sourceNames = new ArrayList<>();
        List<com.graphlab.engine.geo.MatrixSource> sources = new ArrayList<>();
        if (RoutesProxyClient.isConfigured()) {
            sourceNames.add("Google proxy");
            sources.add(new RoutesProxyClient());
        }
        sourceNames.add("road service");
        sources.add(new com.graphlab.data.cloud.OsrmClient());
        RouteService service = new RouteService(new SqliteMatrixCache(container.repository()),
                new com.graphlab.engine.geo.FallbackSource(sourceNames, sources));
        RouteMatrix m = service.matrix(places);
        out.places = m.places();
        StringBuilder sheet = new StringBuilder();
        sheet.append("estimate".equals(m.origin())
                ? getString(R.string.map_origin_estimate, String.valueOf(RouteService.DETOUR))
                        + "\n" + getString(R.string.map_origin_why, service.lastFailure())
                : getString(R.string.map_origin_roads))
                .append("\n\n");
        for (int i = 0; i < out.places.size(); i++) {
            out.markerTitles.add(out.places.get(i).name());
        }
        switch (chosen) {
            case ROUTE: {
                MapTools.Route r = MapTools.shortestRoute(m, 0, m.places().size() - 1);
                out.graph = com.graphlab.engine.geo.CityGraph.readable(m, links(r.path()));
                out.graphName = m.places().get(0).name() + " to " + m.places().get(m.places().size() - 1).name() + " (map)";
                if (!r.reached()) {
                    sheet.append(getString(R.string.map_route_none));
                    break;
                }
                for (int i = 1; i < r.path().size(); i++) {
                    out.lines.add(new double[] {r.path().get(i - 1), r.path().get(i), 1});
                }
                sheet.append(getString(R.string.map_route_line, WeightFormat.format(r.km()))).append('\n');
                for (int i = 0; i < r.path().size(); i++) {
                    sheet.append(getString(R.string.map_place_line, i + 1, m.places().get(r.path().get(i)).name())).append('\n');
                }
                sheet.append('\n').append(getString(R.string.map_route_explored, r.astarExplored(), r.dijkstraExplored()));
                if (!r.heuristicAdmissible()) {
                    sheet.append('\n').append(getString(R.string.map_route_warn));
                }
                break;
            }
            case TRIP: {
                MapTools.Trip t = MapTools.roundTrip(m, 0);
                List<Integer> both = new ArrayList<>(t.exact().order());
                both.add(-1);
                both.addAll(t.nearest().order());
                out.graph = com.graphlab.engine.geo.CityGraph.readable(m, links(both));
                out.graphName = m.places().get(0).name() + " round trip (map)";
                for (int i = 1; i < t.exact().order().size(); i++) {
                    out.lines.add(new double[] {t.exact().order().get(i - 1), t.exact().order().get(i), 1});
                }
                sheet.append(getString(R.string.map_trip_line, WeightFormat.format(round1(t.exact().cost())), t.exact().nodesVisited())).append('\n');
                for (int i = 0; i < t.exact().order().size(); i++) {
                    sheet.append(getString(R.string.map_place_line, i + 1, m.places().get(t.exact().order().get(i)).name())).append('\n');
                }
                if (t.nearest().cost() - t.exact().cost() > 1e-6) {
                    for (int i = 1; i < t.nearest().order().size(); i++) {
                        out.lines.add(new double[] {t.nearest().order().get(i - 1), t.nearest().order().get(i), 3});
                    }
                    sheet.append('\n').append(getString(R.string.map_trip_nearest, WeightFormat.format(round1(t.nearest().cost())),
                            String.format(Locale.US, "%.1f%%", t.excess() * 100.0)));
                } else {
                    sheet.append('\n').append(getString(R.string.map_trip_same));
                }
                break;
            }
            case FLOOD:
            case FLOW: {
                MapFlow.Network f = MapFlow.solve(m);
                out.graph = f.graph();
                out.graphName = (chosen == Tool.FLOOD ? "Flood relief from " : "Flow from ") + m.places().get(0).name() + " to " + m.places().get(m.places().size() - 1).name() + " (map)";
                for (int id = 0; id + 1 < f.graph().edgeCount(); id += 2) {    // links come as two arcs, one per direction
                    Edge e = f.graph().edge(id);
                    int style = f.cutEdgeIds().contains(id) || f.cutEdgeIds().contains(id + 1) ? 2
                            : f.flowEdgeIds().contains(id) || f.flowEdgeIds().contains(id + 1) ? 1 : 0;
                    out.lines.add(new double[] {e.from(), e.to(), style});
                }
                sheet.append(getString(chosen == Tool.FLOOD ? R.string.map_flood_line : R.string.map_flow_line,
                        WeightFormat.format(f.value()),
                        m.places().get(0).name(), m.places().get(m.places().size() - 1).name())).append("\n\n")
                        .append(getString(chosen == Tool.FLOOD ? R.string.map_flood_assumption : R.string.map_flow_assumption,
                                String.valueOf((int) MapFlow.CAPACITY_KM), MapFlow.NEAREST));
                for (int id : f.cutEdgeIds()) {
                    Edge e = f.graph().edge(id);
                    sheet.append('\n').append(getString(R.string.sheet_arrow, m.places().get(e.from()).name(),
                            m.places().get(e.to()).name(), WeightFormat.format(e.weight())));
                }
                break;
            }
            case BUS: {
                MapTools.Network net = MapTools.cheapestNetwork(m);
                MapTools.Route line = MapTools.shortestRoute(m, 0, m.places().size() - 1);
                out.graph = com.graphlab.engine.geo.CityGraph.readable(m, new ArrayList<>());   // the cheapest network is always inside it
                out.graphName = "Bus network of " + m.places().size() + " stops (map)";
                for (int id : net.edgeIds()) {              // the network first, so the line is drawn on top of it
                    Edge e = net.graph().edge(id);
                    out.lines.add(new double[] {e.from(), e.to(), 3});
                }
                sheet.append(getString(R.string.map_bus_network, WeightFormat.format(round1(net.km())), net.edgeIds().size(),
                        m.places().size())).append('\n');
                for (int id : net.edgeIds()) {
                    Edge e = net.graph().edge(id);
                    sheet.append('\n').append(getString(R.string.sheet_edge_line, m.places().get(e.from()).name(),
                            m.places().get(e.to()).name(), WeightFormat.format(e.weight())));
                }
                sheet.append("\n\n");
                if (!line.reached()) {
                    sheet.append(getString(R.string.map_route_none));
                    break;
                }
                for (int i = 1; i < line.path().size(); i++) {
                    out.lines.add(new double[] {line.path().get(i - 1), line.path().get(i), 1});
                }
                sheet.append(getString(R.string.map_bus_line, WeightFormat.format(line.km()), m.places().get(0).name(),
                        m.places().get(m.places().size() - 1).name())).append('\n');
                for (int i = 0; i < line.path().size(); i++) {
                    sheet.append(getString(R.string.map_place_line, i + 1, m.places().get(line.path().get(i)).name())).append('\n');
                }
                break;
            }
            default: {
                MapTools.Network net = MapTools.cheapestNetwork(m);
                out.graph = net.graph();
                out.graphName = "Network of " + m.places().size() + " places (map)";
                for (int id : net.edgeIds()) {
                    Edge e = net.graph().edge(id);
                    out.lines.add(new double[] {e.from(), e.to(), 1});
                }
                sheet.append(getString(R.string.map_net_line, WeightFormat.format(round1(net.km())), net.edgeIds().size()));
                for (int id : net.edgeIds()) {
                    Edge e = net.graph().edge(id);
                    sheet.append('\n').append(getString(R.string.sheet_edge_line, m.places().get(e.from()).name(),
                            m.places().get(e.to()).name(), WeightFormat.format(e.weight())));
                }
            }
        }
        out.sheet = sheet.toString();
        return out;
    }

    /** The links between consecutive places of an order; a -1 in the list ends one order and starts the next. */
    private static List<int[]> links(List<Integer> order) {
        List<int[]> out = new ArrayList<>();
        for (int i = 1; i < order.size(); i++) {
            if (order.get(i) >= 0 && order.get(i - 1) >= 0) {
                out.add(new int[] {order.get(i - 1), order.get(i)});
            }
        }
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private void draw(Outcome o) {
        if (map == null) {
            return;
        }
        resetOverlays();
        float dp = getResources().getDisplayMetrics().density;
        int[] colours = StateColors.resolve(this);
        int idle = colours[State.IDLE.ordinal()];
        int answer = colours[State.PATH.ordinal()];
        int cut = colours[State.FAIL.ordinal()];
        int second = colours[State.CHOSEN.ordinal()];
        // lines first, so the numbered pins sit on top of them
        for (double[] line : o.lines) {
            City a = o.places.get((int) line[0]);
            City b = o.places.get((int) line[1]);
            int style = (int) line[2];
            Polyline poly = new Polyline();
            List<GeoPoint> ends = new ArrayList<>();
            ends.add(new GeoPoint(a.lat(), a.lon()));
            ends.add(new GeoPoint(b.lat(), b.lon()));
            poly.setPoints(ends);
            poly.setOnClickListener((polyline, view, point) -> false);   // a tap near a line still drops a pin
            Paint paint = poly.getOutlinePaint();
            switch (style) {
                case 1:
                    paint.setColor(answer);
                    paint.setStrokeWidth(5f * dp);
                    break;
                case 2:
                    paint.setColor(cut);
                    paint.setStrokeWidth(4f * dp);
                    paint.setPathEffect(new DashPathEffect(new float[] {12f * dp, 7f * dp}, 0f));
                    break;
                case 3:
                    paint.setColor(second);
                    paint.setStrokeWidth(3f * dp);
                    paint.setPathEffect(new DashPathEffect(new float[] {8f * dp, 5f * dp}, 0f));
                    break;
                default:
                    paint.setColor(idle);
                    paint.setStrokeWidth(2f * dp);
            }
            map.getOverlays().add(poly);
        }
        List<GeoPoint> all = new ArrayList<>();
        for (int i = 0; i < o.places.size(); i++) {
            GeoPoint at = new GeoPoint(o.places.get(i).lat(), o.places.get(i).lon());
            all.add(at);
            Marker m = pin(at, i + 1, i == 0, o.markerTitles.get(i));
            m.setOnMarkerClickListener((marker, view) -> true);   // after a run the pins only label; Clear or the list changes them
            map.getOverlays().add(m);
        }
        map.invalidate();
        if (!all.isEmpty()) {
            final BoundingBox box = BoundingBox.fromGeoPoints(all);
            final int margin = Math.round(56 * dp);
            map.post(() -> map.zoomToBoundingBox(box, true, margin));
        }
    }

    /** Saves the graph in the library and opens it in the Lab's Run mode (the map's coordinates become node positions). */
    private void replay() {
        final Outcome o = shown;
        if (o == null || o.graph == null) {
            return;
        }
        final AppContainer container = GraphLabApp.container(this);
        container.executors().disk(() -> {
            GraphDocument doc = new GraphDocument(o.graphName, true, o.graph);
            return container.repository().insertGraph(o.graphName, GraphJson.write(doc), o.graph.nodeCount(), o.graph.edgeCount(),
                    System.currentTimeMillis());
        }, new AppExecutors.Callback<Long>() {
            @Override
            public void onSuccess(Long id) {
                startActivity(new Intent(MapActivity.this, LabActivity.class).putExtra(LabActivity.EXTRA_OPEN_GRAPH_ID, id.longValue())
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                Toast.makeText(MapActivity.this, R.string.map_replay_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
