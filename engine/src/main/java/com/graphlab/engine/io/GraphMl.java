package com.graphlab.engine.io;

import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * GraphML, the XML graph format most graph tools can read and write. Writing produces a plain
 * file with keys for the node label and position and the edge weight. Reading accepts files from
 * other programs too:
 *
 * <ul>
 *   <li>node ids can be any text; a file's own ids like "n7" or "7" are kept as numbers when they
 *       are all unique, otherwise nodes are numbered in file order;</li>
 *   <li>a label comes from a data key named label or name, a position from x and y (or xpos, ypos),
 *       an edge weight from weight, w or cost; anything missing gets a default (the node's own id
 *       as label, a circle layout, weight 1);</li>
 *   <li>the direction comes from {@code edgedefault}; a file that mixes directed and undirected
 *       edges is refused.</li>
 * </ul>
 *
 * <p>Files with a DOCTYPE or entity declarations are refused before parsing, so a hostile file cannot
 * pull in other files or blow up in memory.
 */
public final class GraphMl {
    public static final String NAMESPACE = "http://graphml.graphdrawing.org/xmlns";

    private static final Pattern NUMBERED_NODE = Pattern.compile("n?(\\d{1,9})");
    private static final Pattern NUMBERED_EDGE = Pattern.compile("e?(\\d{1,9})");
    private static final Pattern DOCTYPE = Pattern.compile("<!\\s*(DOCTYPE|ENTITY)", Pattern.CASE_INSENSITIVE);

    private GraphMl() {
    }

    // ---- writing -----------------------------------------------------------------------

    public static String write(GraphDocument doc) {
        Graph graph = doc.graph();
        StringBuilder out = new StringBuilder(512 + 120 * (graph.nodeCount() + graph.edgeCount()));
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<graphml xmlns=\"").append(NAMESPACE).append("\">\n");
        out.append("  <key id=\"d_gl\" for=\"graph\" attr.name=\"graphlab\" attr.type=\"int\"/>\n");
        out.append("  <key id=\"d_label\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n");
        out.append("  <key id=\"d_x\" for=\"node\" attr.name=\"x\" attr.type=\"double\"/>\n");
        out.append("  <key id=\"d_y\" for=\"node\" attr.name=\"y\" attr.type=\"double\"/>\n");
        if (doc.weighted()) {
            out.append("  <key id=\"d_weight\" for=\"edge\" attr.name=\"weight\" attr.type=\"double\"/>\n");
        }
        out.append("  <graph id=\"G\" edgedefault=\"").append(graph.isDirected() ? "directed" : "undirected").append("\">\n");
        out.append("    <desc>").append(escape(doc.name())).append("</desc>\n");
        out.append("    <data key=\"d_gl\">1</data>\n");   // marks a GraphLab file: its positions are trusted as they are
        for (Node node : graph.nodes()) {
            out.append("    <node id=\"n").append(node.id()).append("\">")
                    .append("<data key=\"d_label\">").append(escape(node.label())).append("</data>")
                    .append("<data key=\"d_x\">").append(MiniJson.number(node.x())).append("</data>")
                    .append("<data key=\"d_y\">").append(MiniJson.number(node.y())).append("</data>")
                    .append("</node>\n");
        }
        for (Edge edge : graph.edges()) {
            out.append("    <edge id=\"e").append(edge.id()).append("\" source=\"n").append(edge.from())
                    .append("\" target=\"n").append(edge.to()).append("\">");
            if (doc.weighted()) {
                out.append("<data key=\"d_weight\">").append(MiniJson.number(edge.weight())).append("</data>");
            }
            out.append("</edge>\n");
        }
        out.append("  </graph>\n");
        out.append("</graphml>\n");
        return out.toString();
    }

    /** Escapes the five XML characters and drops characters XML 1.0 cannot hold at all. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean allowed = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (!allowed) {
                continue;
            }
            switch (cp) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    // ---- reading -----------------------------------------------------------------------

    public static GraphDocument read(String text) throws GraphFormatException {
        if (DOCTYPE.matcher(text).find()) {
            throw new GraphFormatException("GraphML files with a DOCTYPE or entity declarations are not supported.");
        }
        Document xml = parseXml(text);
        Element root = xml.getDocumentElement();
        if (!"graphml".equals(local(root))) {
            throw new GraphFormatException("This is XML, but not GraphML (the first element is <" + local(root) + ">).");
        }

        Map<String, KeyInfo> keys = new HashMap<>();
        List<Element> graphs = new ArrayList<>();
        for (Element child : children(root)) {
            if ("key".equals(local(child))) {
                String id = child.getAttribute("id");
                String name = child.getAttribute("attr.name").toLowerCase(Locale.ROOT);
                String domain = child.getAttribute("for");
                keys.put(id, new KeyInfo(name, domain.isEmpty() ? "all" : domain));
            } else if ("graph".equals(local(child))) {
                graphs.add(child);
            }
        }
        if (graphs.isEmpty()) {
            throw new GraphFormatException("The GraphML file contains no <graph>.");
        }
        if (graphs.size() > 1) {
            throw new GraphFormatException("The GraphML file contains more than one graph. GraphLab opens one at a time.");
        }
        Element graphEl = graphs.get(0);
        String edgeDefault = graphEl.getAttribute("edgedefault").toLowerCase(Locale.ROOT);
        if (!edgeDefault.isEmpty() && !edgeDefault.equals("directed") && !edgeDefault.equals("undirected")) {
            throw new GraphFormatException("edgedefault must be directed or undirected, not \"" + edgeDefault + "\".");
        }
        boolean directed = edgeDefault.equals("directed");

        String name = graphName(graphEl, keys);
        boolean ownFile = isGraphLabFile(graphEl, keys);
        List<RawNode> rawNodes = new ArrayList<>();
        List<RawEdge> rawEdges = new ArrayList<>();
        for (Element child : children(graphEl)) {
            String tag = local(child);
            if ("node".equals(tag)) {
                for (Element inner : children(child)) {
                    if ("graph".equals(local(inner))) {
                        throw new GraphFormatException("Nested graphs are not supported.");
                    }
                }
                rawNodes.add(readNode(child, keys, rawNodes.size() + 1));
            } else if ("edge".equals(tag)) {
                rawEdges.add(readEdge(child, keys, rawEdges.size() + 1, directed));
            } else if ("hyperedge".equals(tag)) {
                throw new GraphFormatException("Hyperedges are not supported.");
            }
        }
        if (rawNodes.size() > Graph.MAX_NODES) {
            throw new GraphFormatException("The graph has " + rawNodes.size()
                    + " nodes, but GraphLab supports at most " + Graph.MAX_NODES + ".");
        }

        // Node ids: keep the file's numbers when they are all like n7 or 7 and unique.
        int[] nodeIds = numberedIds(rawNodes.stream().map(n -> n.id).toList(), NUMBERED_NODE);
        Map<String, Integer> idOf = new HashMap<>();
        for (int i = 0; i < rawNodes.size(); i++) {
            if (idOf.put(rawNodes.get(i).id, nodeIds[i]) != null) {
                throw new GraphFormatException("Two nodes have the id \"" + rawNodes.get(i).id + "\".");
            }
        }

        List<Node> nodes = new ArrayList<>(rawNodes.size());
        boolean positionsMissing = false;
        for (int i = 0; i < rawNodes.size(); i++) {
            RawNode raw = rawNodes.get(i);
            String label = raw.label != null && !raw.label.isEmpty() ? raw.label
                    : raw.id.length() <= 8 ? raw.id : GraphEditor.labelFor(i);
            positionsMissing |= raw.x == null || raw.y == null;
            nodes.add(new Node(nodeIds[i], raw.x == null ? 0 : raw.x, raw.y == null ? 0 : raw.y, label));
        }
        if (positionsMissing) {
            nodes = GraphLayouts.circle(nodes);
        } else if (!ownFile) {
            nodes = GraphLayouts.fitForeign(nodes);   // other programs use other units; our own files are kept exactly
        }

        int[] edgeIds = numberedIds(rawEdges.stream().map(e -> e.id).toList(), NUMBERED_EDGE);
        boolean anyWeight = false;
        List<Edge> edges = new ArrayList<>(rawEdges.size());
        for (int i = 0; i < rawEdges.size(); i++) {
            RawEdge raw = rawEdges.get(i);
            Integer from = idOf.get(raw.source);
            Integer to = idOf.get(raw.target);
            if (from == null || to == null) {
                throw new GraphFormatException("Edge " + (i + 1) + " refers to the node \""
                        + (from == null ? raw.source : raw.target) + "\", which is not in the file.");
            }
            anyWeight |= raw.weight != null;
            edges.add(new Edge(edgeIds[i], from, to, raw.weight == null ? GraphEditor.DEFAULT_WEIGHT : raw.weight));
        }
        return new GraphDocument(name, anyWeight, GraphChecks.build(directed, nodes, edges));
    }

    // ---- reading helpers ---------------------------------------------------------------

    private static Document parseXml(String text) throws GraphFormatException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (ParserConfigurationException | RuntimeException ignored) {
                // Not every parser knows this feature; the text check above already refused DOCTYPE.
            }
            try {
                factory.setXIncludeAware(false);
            } catch (UnsupportedOperationException ignored) {
                // fine
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return builder.parse(new InputSource(new StringReader(text)));
        } catch (SAXParseException e) {
            throw new GraphFormatException("This is not valid XML (line " + e.getLineNumber() + "): " + e.getMessage(), e);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new GraphFormatException("This is not valid XML: " + e.getMessage(), e);
        }
    }

    private static String graphName(Element graphEl, Map<String, KeyInfo> keys) {
        for (Element child : children(graphEl)) {
            if ("desc".equals(local(child))) {
                String cleaned = GraphNames.clean(child.getTextContent());
                if (!cleaned.isEmpty()) {
                    return cleaned;
                }
            }
            if ("data".equals(local(child))) {
                KeyInfo info = keys.get(child.getAttribute("key"));
                if (info != null && (info.name.equals("name") || info.name.equals("label"))) {
                    String cleaned = GraphNames.clean(child.getTextContent());
                    if (!cleaned.isEmpty()) {
                        return cleaned;
                    }
                }
            }
        }
        String id = GraphNames.clean(graphEl.getAttribute("id"));
        return id.isEmpty() || id.equals("G") ? GraphNames.IMPORTED : id;
    }

    /** True for a file this app wrote: it carries the "graphlab" key on its graph. */
    private static boolean isGraphLabFile(Element graphEl, Map<String, KeyInfo> keys) {
        for (Element child : children(graphEl)) {
            if ("data".equals(local(child))) {
                KeyInfo info = keys.get(child.getAttribute("key"));
                if (info != null && info.name.equals("graphlab")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RawNode readNode(Element el, Map<String, KeyInfo> keys, int position) throws GraphFormatException {
        String id = el.getAttribute("id");
        if (id.isEmpty()) {
            throw new GraphFormatException("Node " + position + " has no id.");
        }
        RawNode node = new RawNode(id);
        for (Element data : children(el)) {
            if (!"data".equals(local(data))) {
                continue;
            }
            KeyInfo info = keys.get(data.getAttribute("key"));
            if (info == null) {
                continue;
            }
            String value = data.getTextContent().trim();
            switch (info.name) {
                case "label":
                case "name":
                    if (node.label == null) {
                        node.label = value;
                    }
                    break;
                case "x":
                case "xpos":
                    node.x = number(value, "Node \"" + id + "\" x");
                    break;
                case "y":
                case "ypos":
                    node.y = number(value, "Node \"" + id + "\" y");
                    break;
                default:
                    break;
            }
        }
        return node;
    }

    private static RawEdge readEdge(Element el, Map<String, KeyInfo> keys, int position, boolean graphDirected)
            throws GraphFormatException {
        String source = el.getAttribute("source");
        String target = el.getAttribute("target");
        if (source.isEmpty() || target.isEmpty()) {
            throw new GraphFormatException("Edge " + position + " needs both a source and a target.");
        }
        String own = el.getAttribute("directed");
        if (!own.isEmpty() && Boolean.parseBoolean(own) != graphDirected) {
            throw new GraphFormatException("Edge " + position + " is "
                    + (graphDirected ? "undirected" : "directed")
                    + " in a "
                    + (graphDirected ? "directed" : "undirected")
                    + " graph. GraphLab does not support mixed graphs.");
        }
        RawEdge edge = new RawEdge(el.getAttribute("id"), source, target);
        for (Element data : children(el)) {
            if (!"data".equals(local(data))) {
                continue;
            }
            KeyInfo info = keys.get(data.getAttribute("key"));
            if (info != null && (info.name.equals("weight") || info.name.equals("w") || info.name.equals("cost"))) {
                String value = data.getTextContent().trim();
                if (!value.isEmpty()) {
                    edge.weight = number(value, "Edge " + position + " weight");
                }
            }
        }
        return edge;
    }

    private static Double number(String value, String what) throws GraphFormatException {
        try {
            double d = Double.parseDouble(value);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new GraphFormatException(what + " is not a finite number.");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new GraphFormatException(what + " \"" + value + "\" is not a number.");
        }
    }

    /**
     * If every id matches the pattern (n7, 7) and the numbers are unique, returns those numbers.
     * Otherwise 0, 1, 2 ... in file order.
     */
    private static int[] numberedIds(List<String> ids, Pattern pattern) {
        int[] numbers = new int[ids.size()];
        Set<Integer> seen = new HashSet<>();
        boolean keep = true;
        for (int i = 0; i < ids.size() && keep; i++) {
            java.util.regex.Matcher m = pattern.matcher(ids.get(i));
            if (m.matches()) {
                numbers[i] = Integer.parseInt(m.group(1));
                keep = seen.add(numbers[i]);
            } else {
                keep = false;
            }
        }
        if (!keep) {
            for (int i = 0; i < numbers.length; i++) {
                numbers[i] = i;
            }
        }
        return numbers;
    }

    private static List<Element> children(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList all = parent.getChildNodes();
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element) {
                out.add((Element) all.item(i));
            }
        }
        return out;
    }

    private static String local(Element e) {
        return e.getLocalName() != null ? e.getLocalName() : e.getNodeName();
    }

    private static final class KeyInfo {
        final String name;
        final String domain;

        KeyInfo(String name, String domain) {
            this.name = name;
            this.domain = domain;
        }
    }

    private static final class RawNode {
        final String id;
        String label;
        Double x;
        Double y;

        RawNode(String id) {
            this.id = id;
        }
    }

    private static final class RawEdge {
        final String id;
        final String source;
        final String target;
        Double weight;

        RawEdge(String id, String source, String target) {
            this.id = id;
            this.source = source;
            this.target = target;
        }
    }
}
