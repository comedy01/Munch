package dev.munch.core;

public final class Chews {
    private static final int INTERVAL = 4;
    private static final float START_FRACTION = 0.21875F;

    private Chews() {
    }

    public static int total(int duration) {
        return done(duration, 0);
    }

    public static int done(int duration, int remaining) {
        int from = Math.max(remaining + 1, 1);
        int to = duration - (int) (duration * START_FRACTION) - 1;
        if (to < from) {
            return 0;
        }
        return Math.floorDiv(to, INTERVAL) - Math.floorDiv(from - 1, INTERVAL);
    }
}
