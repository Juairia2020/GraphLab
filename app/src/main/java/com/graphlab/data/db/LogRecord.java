package com.graphlab.data.db;

import androidx.annotation.NonNull;

/** One Activity lifecycle event, as listed on the lifecycle log screen. */
public final class LogRecord {
    public final long id;
    public final long at;
    @NonNull
    public final String source;
    @NonNull
    public final String event;

    public LogRecord(long id, long at, @NonNull String source, @NonNull String event) {
        this.id = id;
        this.at = at;
        this.source = source;
        this.event = event;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LogRecord)) {
            return false;
        }
        LogRecord that = (LogRecord) other;
        return id == that.id && at == that.at && source.equals(that.source) && event.equals(that.event);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id) * 31 + event.hashCode();
    }
}
