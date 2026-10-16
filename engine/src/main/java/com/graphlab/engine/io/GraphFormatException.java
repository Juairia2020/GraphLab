package com.graphlab.engine.io;

/**
 * A file or text that cannot be turned into a graph. The message is written for the person who
 * picked the file ("Edge 3 joins node D to itself ..."), so the screen can show it as it is.
 * Checked on purpose: every caller must decide what to tell the user.
 */
public class GraphFormatException extends Exception {
    private static final long serialVersionUID = 1L;

    public GraphFormatException(String message) {
        super(message);
    }

    public GraphFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
