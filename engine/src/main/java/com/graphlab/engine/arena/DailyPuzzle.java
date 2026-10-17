package com.graphlab.engine.arena;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The daily puzzle (MS17): the same five questions for everyone on one calendar day. The day is a UTC date written as
 * {@code yyyy-MM-dd}. The seed is the first eight bytes of SHA-256 over the UTF-8 text {@code "graphlab-daily-" + date}, so it
 * does not depend on the phone's language, time zone or Java version ({@code String.hashCode} would also be stable, but a hash
 * of the bytes is the safer habit). Card activities are left out because the card bank can differ between app versions.
 */
public final class DailyPuzzle {
    public static final Level LEVEL = Level.MEDIUM;
    public static final int QUESTIONS = ArenaRound.QUESTIONS;
    public static final String SALT = "graphlab-daily-";

    private DailyPuzzle() {
    }

    /** The activities a daily puzzle may use: not the card activities, in the order of {@link QuestionKind}. */
    public static List<QuestionKind> pool() {
        List<QuestionKind> out = new ArrayList<>();
        for (QuestionKind k : QuestionKind.values()) {
            if (!k.usesCardBank()) {
                out.add(k);
            }
        }
        return out;
    }

    /** The UTC date of a moment as {@code yyyy-MM-dd}. */
    public static String dateString(long epochMillis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC).toString();
    }

    /** @throws IllegalArgumentException if the text is not a real date written as yyyy-MM-dd */
    public static String normalise(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("A daily puzzle date looks like 2026-10-15, got: " + date);
        }
        try {
            return LocalDate.parse(date).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Not a real date: " + date);
        }
    }

    /** The seed of a day. */
    public static long seed(String date) {
        String day = normalise(date);
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((SALT + day).getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (h[i] & 0xFF);
            }
            return v;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }

    /** The specs of a day from the whole pool: five different activities, each with its own seed. */
    public static List<QuestionSpec> specs(String date) {
        return specs(date, pool());
    }

    /**
     * The specs of a day from the activities the player has unlocked: different activities, each with its own seed, up to
     * {@link #QUESTIONS} (fewer when fewer are open). Card activities are always left out. The same date and the same open
     * activities always give the same specs, and the whole pool gives exactly the puzzle of {@link #specs(String)}.
     */
    public static List<QuestionSpec> specs(String date, java.util.Collection<QuestionKind> open) {
        Random random = new Random(seed(date));
        List<QuestionKind> rest = new ArrayList<>();
        for (QuestionKind k : pool()) {
            if (open.contains(k)) {
                rest.add(k);
            }
        }
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("No open activity can be used in a daily puzzle");
        }
        int count = Math.min(QUESTIONS, rest.size());
        List<QuestionSpec> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            QuestionKind kind = rest.remove(random.nextInt(rest.size()));
            out.add(new QuestionSpec(kind, LEVEL, random.nextLong()));
        }
        return out;
    }

    /** The round of a day. */
    public static ArenaRound round(String date) {
        return ArenaRound.fromSpecs(LEVEL, seed(date), specs(date));
    }
}
