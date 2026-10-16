package com.graphlab.engine.io;

import java.util.Objects;

/**
 * A photo behind the graph (MS10 S6): the file name inside the app's own folder, how see-through it is, and whether it is
 * locked. The photo is copied into app storage when it is picked, so the graph only needs the name, and the app never needs
 * a storage permission. Only a plain file name is allowed (no folders, no "..") so a saved or imported graph can never point
 * outside that folder. Plain Java, no Android.
 *
 * @param file    the file name, letters, digits, dot, dash and underscore only, at most {@value #MAX_NAME} characters
 * @param opacity 0 (invisible) to 1 (solid)
 * @param locked  true freezes the photo settings so a stray tap cannot change or remove it
 */
public record Background(String file, double opacity, boolean locked) {
    public static final int MAX_NAME = 80;
    public static final double DEFAULT_OPACITY = 0.5;
    /** The photo is drawn this many world units wide, centred on (0, 0); its height follows from its shape. */
    public static final double WORLD_WIDTH = 1000.0;

    public Background {
        Objects.requireNonNull(file, "file");
        if (!isSafeName(file)) {
            throw new IllegalArgumentException("Not a plain photo file name: " + file);
        }
        if (Double.isNaN(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("Opacity must be between 0 and 1, got " + opacity);
        }
    }

    public static Background of(String file) {
        return new Background(file, DEFAULT_OPACITY, false);
    }

    public Background withOpacity(double newOpacity) {
        return new Background(file, newOpacity, locked);
    }

    public Background withLocked(boolean newLocked) {
        return new Background(file, opacity, newLocked);
    }

    /** True for a plain file name that cannot escape the photo folder. */
    public static boolean isSafeName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME || name.startsWith(".")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return !name.contains("..");
    }

    /** The power-of-two shrink factor that brings the longer side of a picture to {@code maxSide} pixels or less. */
    public static int sampleSize(int width, int height, int maxSide) {
        if (width < 1 || height < 1 || maxSide < 1) {
            throw new IllegalArgumentException("Sizes must be positive.");
        }
        int size = 1;
        int longer = Math.max(width, height);
        while (longer / size > maxSide) {
            size *= 2;
        }
        return size;
    }

    /** {left, top, right, bottom} of the photo in world units for a picture of this size in pixels. */
    public static double[] worldRect(int pixelWidth, int pixelHeight) {
        if (pixelWidth < 1 || pixelHeight < 1) {
            throw new IllegalArgumentException("A picture needs a positive size.");
        }
        double height = WORLD_WIDTH * pixelHeight / pixelWidth;
        return new double[] {-WORLD_WIDTH / 2, -height / 2, WORLD_WIDTH / 2, height / 2};
    }
}
