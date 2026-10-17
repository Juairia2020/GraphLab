package com.graphlab.engine.content;

/** Content (questions or levels) that cannot be used. The message says which item and what is wrong. */
public final class ContentException extends Exception {
    public ContentException(String message) {
        super(message);
    }
}
