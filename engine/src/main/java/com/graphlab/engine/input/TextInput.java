package com.graphlab.engine.input;

import java.util.Objects;

/** A question that is only text: a statement to judge or a situation to choose a tool for (MS6 cards). */
public record TextInput(String body) implements Input {
    public TextInput {
        Objects.requireNonNull(body, "body");
        if (body.isBlank()) {
            throw new IllegalArgumentException("A text question needs some text");
        }
    }

    @Override
    public InputKind kind() {
        return InputKind.TEXT;
    }
}
