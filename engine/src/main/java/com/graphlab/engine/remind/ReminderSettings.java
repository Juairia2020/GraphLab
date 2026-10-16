package com.graphlab.engine.remind;

import java.util.Objects;

/** The daily reminder: on or off, and the time of day. Saved as the text "1,18,30". */
public final class ReminderSettings {
    public static final int DEFAULT_HOUR = 18;
    public static final int DEFAULT_MINUTE = 0;

    private final boolean enabled;
    private final int hour;
    private final int minute;

    private ReminderSettings(boolean enabled, int hour, int minute) {
        this.enabled = enabled;
        this.hour = hour;
        this.minute = minute;
    }

    /** @throws IllegalArgumentException if the time is not a real time of day */
    public static ReminderSettings of(boolean enabled, int hour, int minute) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("The hour must be 0 to 23, not " + hour);
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("The minute must be 0 to 59, not " + minute);
        }
        return new ReminderSettings(enabled, hour, minute);
    }

    /** Off, at the default time. */
    public static ReminderSettings off() {
        return new ReminderSettings(false, DEFAULT_HOUR, DEFAULT_MINUTE);
    }

    public boolean enabled() {
        return enabled;
    }

    public int hour() {
        return hour;
    }

    public int minute() {
        return minute;
    }

    public ReminderSettings withEnabled(boolean on) {
        return new ReminderSettings(on, hour, minute);
    }

    public ReminderSettings withTime(int newHour, int newMinute) {
        return of(enabled, newHour, newMinute);
    }

    public String encode() {
        return (enabled ? "1" : "0") + "," + hour + "," + minute;
    }

    /** Reads {@link #encode()}. Anything unreadable gives {@link #off()}, so a damaged file never blocks the app. */
    public static ReminderSettings decode(String text) {
        if (text == null) {
            return off();
        }
        String[] parts = text.trim().split(",");
        if (parts.length != 3) {
            return off();
        }
        try {
            boolean on = parts[0].trim().equals("1");
            if (!on && !parts[0].trim().equals("0")) {
                return off();
            }
            return of(on, Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (IllegalArgumentException e) {
            return off();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReminderSettings)) {
            return false;
        }
        ReminderSettings that = (ReminderSettings) other;
        return enabled == that.enabled && hour == that.hour && minute == that.minute;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, hour, minute);
    }

    @Override
    public String toString() {
        return "ReminderSettings{" + encode() + "}";
    }
}
