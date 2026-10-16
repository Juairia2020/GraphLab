package com.graphlab.engine.io;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a graph typed as plain text, one edge per line:
 * <pre>
 * # a comment
 * directed            (or "undirected"; the default is the one the caller passes)
 * A B 4               an edge from A to B with weight 4
 * B C                 an edge with no weight (weight 1)
 * C -&gt; D 2.5          an arrow also means "directed"
 * E                   a node with no edges
 * </pre>
 * Node names are any words of up to {@value #MAX_NAME} characters. They become the node labels, in order of first
 * appearance. If any edge has a weight the graph is weighted, and edges without one get weight 1. Nodes are placed on a
 * circle. Every problem is reported with its line number, in words meant for the person who typed it.
 */
public final class EdgeListParser {
    public static final int MAX_NAME = 12;
    public static final int MAX_EDGES = 400;
    public static final int MAX_CHARS = 20_000;

    /** What the import dialog shows as an example. */
    public static final String EXAMPLE = "# one edge per line: from to [weight]\nA B 4\nA C 2\nB C 1\nB D 5\nC D 8\n";

    private EdgeListParser() {
    }

    /**
     * @param directedDefault used unless the text says "directed", "undirected" or uses an arrow
     * @throws GraphFormatException if the text is empty, too big, or has a bad line
     */
    public static GraphDocument parse(String text, boolean directedDefault) throws GraphFormatException {
        if (text == null || text.isBlank()) {
            throw new GraphFormatException("The text is empty. Type one edge per line, like: A B 4");
        }
        if (text.length() > MAX_CHARS) {
            throw new GraphFormatException("The text is too long (more than " + MAX_CHARS + " characters).");
        }
        Boolean directive = null;
        boolean arrow = false;
        boolean anyWeight = false;
        Map<String, Integer> ids = new HashMap<>();
        List<String> names = new ArrayList<>();
        List<int[]> ends = new ArrayList<>();      // from, to
        List<Double> weights = new ArrayList<>();
        List<Integer> lineOf = new ArrayList<>();

        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i];
            int cut = indexOfComment(line);
            if (cut >= 0) {
                line = line.substring(0, cut);
            }
            if (line.contains("->")) {
                arrow = true;
                line = line.replace("->", " ");
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split("[\\s,;]+");
            if (t.length == 1 && (t[0].equalsIgnoreCase("directed") || t[0].equalsIgnoreCase("undirected"))) {
                boolean now = t[0].equalsIgnoreCase("directed");
                if (directive != null && directive != now) {
                    throw new GraphFormatException("Line " + lineNo + ": the text says both \"directed\" and \"undirected\".");
                }
                directive = now;
                continue;
            }
            if (t.length > 3) {
                throw new GraphFormatException("Line " + lineNo + ": too many words. Use: from to [weight].");
            }
            for (int k = 0; k < Math.min(t.length, 2); k++) {
                checkName(t[k], lineNo);
            }
            int from = idOf(t[0], ids, names, lineNo);
            if (t.length == 1) {
                continue;   // a node on its own
            }
            int to = idOf(t[1], ids, names, lineNo);
            double weight = 1.0;
            if (t.length == 3) {
                weight = weightOf(t[2], lineNo);
                anyWeight = true;
            }
            if (from == to) {
                throw new GraphFormatException("Line " + lineNo + ": " + t[0] + " is joined to itself. Self-loops are not supported.");
            }
            ends.add(new int[] {from, to});
            weights.add(weight);
            lineOf.add(lineNo);
            if (ends.size() > MAX_EDGES) {
                throw new GraphFormatException("Line " + lineNo + ": more than " + MAX_EDGES + " edges.");
            }
        }
        if (names.isEmpty()) {
            throw new GraphFormatException("The text has no nodes. Type one edge per line, like: A B 4");
        }
        if (names.size() > Graph.MAX_NODES) {
            throw new GraphFormatException("The text has " + names.size() + " nodes, but GraphLab supports at most " + Graph.MAX_NODES + ".");
        }
        boolean directed = directive != null ? directive : (arrow || directedDefault);

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            nodes.add(new Node(i, 0, 0, names.get(i)));
        }
        List<Edge> edges = new ArrayList<>();
        Map<Long, Integer> seen = new HashMap<>();
        for (int e = 0; e < ends.size(); e++) {
            int a = ends.get(e)[0];
            int b = ends.get(e)[1];
            long key = directed || a < b ? (long) a * 1000 + b : (long) b * 1000 + a;
            Integer first = seen.put(key, lineOf.get(e));
            if (first != null) {
                throw new GraphFormatException("Line " + lineOf.get(e) + ": " + names.get(a) + " and " + names.get(b)
                        + " are already joined (line " + first + ").");
            }
            edges.add(new Edge(e, a, b, weights.get(e)));
        }
        Graph graph = GraphChecks.build(directed, GraphLayouts.circle(nodes), edges);
        return new GraphDocument(GraphNames.IMPORTED, anyWeight, graph);
    }

    private static int indexOfComment(String line) {
        int hash = line.indexOf('#');
        int slashes = line.indexOf("//");
        if (hash < 0) {
            return slashes;
        }
        return slashes < 0 ? hash : Math.min(hash, slashes);
    }

    private static void checkName(String name, int lineNo) throws GraphFormatException {
        if (name.length() > MAX_NAME) {
            throw new GraphFormatException("Line " + lineNo + ": the name \"" + name + "\" is longer than " + MAX_NAME + " characters.");
        }
    }

    private static int idOf(String name, Map<String, Integer> ids, List<String> names, int lineNo) throws GraphFormatException {
        Integer id = ids.get(name);
        if (id == null) {
            if (names.size() >= Graph.MAX_NODES) {
                throw new GraphFormatException("Line " + lineNo + ": more than " + Graph.MAX_NODES + " nodes.");
            }
            id = names.size();
            ids.put(name, id);
            names.add(name);
        }
        return id;
    }

    private static double weightOf(String word, int lineNo) throws GraphFormatException {
        try {
            double w = Double.parseDouble(word.toLowerCase(Locale.ROOT));
            if (!Double.isFinite(w)) {
                throw new NumberFormatException();
            }
            return w;
        } catch (NumberFormatException e) {
            throw new GraphFormatException("Line " + lineNo + ": \"" + word + "\" is not a weight. Use a number such as 4 or -2.5.");
        }
    }
}
