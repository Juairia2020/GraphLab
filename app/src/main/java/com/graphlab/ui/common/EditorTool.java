package com.graphlab.ui.common;

/**
 * What a touch does on the graph canvas. The tool decides which gesture means what, so
 * panning and moving a node never fight each other:
 *
 * <table>
 *   <tr><td></td><td>tap</td><td>drag from a node</td><td>drag from empty space</td></tr>
 *   <tr><td>NODE</td><td>add a node</td><td>pan</td><td>pan</td></tr>
 *   <tr><td>EDGE</td><td>pick the two ends; tap an edge for its weight</td><td>draw an edge</td><td>pan</td></tr>
 *   <tr><td>MOVE</td><td>tap an edge for its weight</td><td>move the node</td><td>pan</td></tr>
 *   <tr><td>DELETE</td><td>delete the node or edge</td><td>pan</td><td>pan</td></tr>
 * </table>
 *
 * <p>Two-finger pinch zooms (and pans) in every tool.
 */
public enum EditorTool {
    NODE, EDGE, MOVE, DELETE
}
