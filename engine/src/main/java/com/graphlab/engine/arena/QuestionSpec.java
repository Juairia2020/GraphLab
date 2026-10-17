package com.graphlab.engine.arena;

import java.util.Objects;

/** What is needed to make one question again: its activity, level and seed. The same spec always gives the same question. */
public record QuestionSpec(QuestionKind kind, Level level, long seed) {
    public QuestionSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(level, "level");
    }

    /** The question this spec stands for. */
    public Question create() {
        return QuestionFactory.create(kind, level, seed);
    }

    /** Text for an intent or a saved state: {@code kind-id:LEVEL:seed} for each spec, joined by semicolons. */
    public static String encode(java.util.List<QuestionSpec> specs) {
        StringBuilder out = new StringBuilder();
        for (QuestionSpec s : specs) {
            out.append(out.length() == 0 ? "" : ";").append(s.kind().id()).append(':').append(s.level().name()).append(':')
                    .append(s.seed());
        }
        return out.toString();
    }

    /** @throws IllegalArgumentException if the text is not what {@link #encode} makes */
    public static java.util.List<QuestionSpec> decode(String text) {
        java.util.List<QuestionSpec> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("No questions");
        }
        for (String part : text.split(";", -1)) {
            String[] f = part.split(":", -1);
            if (f.length != 3) {
                throw new IllegalArgumentException("Bad question spec: " + part);
            }
            QuestionKind kind = QuestionKind.byId(f[0]);
            try {
                out.add(new QuestionSpec(kind, Level.valueOf(f[1]), Long.parseLong(f[2])));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad seed in: " + part);
            }
        }
        return out;
    }
}
