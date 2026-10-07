package dev.munch.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {
    @Test
    void chewsMatchVanillaSounds() {
        assertEquals(6, Chews.total(32));
        assertEquals(3, Chews.total(16));
        assertEquals(0, Chews.done(32, 32));
        assertEquals(0, Chews.done(32, 25));
        assertEquals(1, Chews.done(32, 23));
        assertEquals(6, Chews.done(32, 0));
        for (int duration = 1; duration <= 200; duration++) {
            for (int remaining = 0; remaining <= duration; remaining++) {
                assertEquals(slowChews(duration, remaining), Chews.done(duration, remaining),
                        "duration " + duration + ", remaining " + remaining);
            }
        }
    }

    private static int slowChews(int duration, int remaining) {
        int count = 0;
        for (int r = Math.max(remaining + 1, 1); r <= duration; r++) {
            int used = duration - r;
            if (used > (int) (duration * 0.21875F) && r % 4 == 0) {
                count++;
            }
        }
        return count;
    }

    @Test
    void bitesGrowEveryChewAndLeaveAPiece() {
        int size = 16;
        boolean[] opaque = disc(size, 6.5);
        int total = count(opaque);
        int stages = 6;
        int[] removedAt = Bites.plan(opaque, size, size, stages, 42L);
        int previous = 0;
        for (int stage = 1; stage <= stages; stage++) {
            int gone = 0;
            for (int i = 0; i < removedAt.length; i++) {
                assertFalse(removedAt[i] > 0 && !opaque[i], "a see-through pixel was bitten");
                if (removedAt[i] > 0 && removedAt[i] <= stage) {
                    gone++;
                }
            }
            assertTrue(gone > previous, "stage " + stage + " took no bite");
            previous = gone;
        }
        assertTrue(previous < total, "nothing was left after the last chew");
        assertTrue(total - previous >= total / 20, "the last piece is too small: " + (total - previous));
    }

    @Test
    void biteNeverLeavesLooseFragments() {
        int size = 16;
        boolean[] opaque = new boolean[size * size];
        for (int y = 2; y < 14; y++) {
            for (int x = 2; x < 14; x++) {
                opaque[y * size + x] = true;
            }
        }
        for (int y = 0; y < 2; y++) {
            opaque[y * size + 12] = true;
        }
        for (long seed = 0; seed < 200; seed++) {
            int[] removedAt = Bites.plan(opaque, size, size, 6, seed);
            for (int stage = 0; stage <= 6; stage++) {
                boolean[] left = new boolean[opaque.length];
                for (int i = 0; i < opaque.length; i++) {
                    left[i] = opaque[i] && !(removedAt[i] > 0 && removedAt[i] <= stage);
                }
                assertEquals(1, pieces(left, size), "seed " + seed + " stage " + stage + " left a loose piece");
            }
        }
    }

    @Test
    void separatePartsSurviveUntilBitten() {
        int size = 16;
        boolean[] opaque = disc(size, 5);
        int corner = (size - 1) * size;
        opaque[corner] = true;
        int[] removedAt = Bites.plan(opaque, size, size, 6, 3L);
        assertEquals(0, removedAt[corner], "an untouched separate pixel was dropped");
    }

    private static int pieces(boolean[] cells, int width) {
        boolean[] seen = new boolean[cells.length];
        int pieces = 0;
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int start = 0; start < cells.length; start++) {
            if (!cells[start] || seen[start]) {
                continue;
            }
            pieces++;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                int x = i % width;
                int[] around = {x > 0 ? i - 1 : -1, x < width - 1 ? i + 1 : -1, i - width, i + width};
                for (int n : around) {
                    if (n >= 0 && n < cells.length && cells[n] && !seen[n]) {
                        seen[n] = true;
                        queue.add(n);
                    }
                }
            }
        }
        return pieces;
    }

    @Test
    void bitesStartTopRight() {
        int size = 16;
        boolean[] opaque = new boolean[size * size];
        java.util.Arrays.fill(opaque, true);
        int[] removedAt = Bites.plan(opaque, size, size, 6, 7L);
        assertTrue(removedAt[size - 1] == 1, "the first bite missed the top right corner");
        assertEquals(0, removedAt[(size - 1) * size], "the bottom left corner was eaten");
    }

    @Test
    void bitesAreTheSameEveryTime() {
        boolean[] opaque = disc(16, 7);
        assertTrue(java.util.Arrays.equals(Bites.plan(opaque, 16, 16, 6, 5L), Bites.plan(opaque, 16, 16, 6, 5L)));
    }

    @Test
    void drainEmptiesFromTheTop() {
        int w = 4;
        int h = 4;
        boolean[] liquid = new boolean[w * h];
        for (int y = 1; y < 4; y++) {
            liquid[y * w + 1] = true;
            liquid[y * w + 2] = true;
        }
        int[] removedAt = Drain.plan(liquid, w, h);
        assertEquals(3, Drain.stages(removedAt));
        assertEquals(1, removedAt[w + 1]);
        assertEquals(3, removedAt[3 * w + 2]);
        assertEquals(0, removedAt[0]);
        assertEquals(0, Drain.stage(3, 0.0F));
        assertEquals(3, Drain.stage(3, 1.0F));
    }

    @Test
    void planScalesToLargerSprites() {
        int[] removedAt = new int[4];
        removedAt[1] = 2;
        Plan plan = new Plan(2, 2, removedAt, 2);
        assertFalse(plan.removed(3, 0, 4, 4, 1));
        assertTrue(plan.removed(3, 0, 4, 4, 2));
        assertFalse(plan.removed(0, 0, 4, 4, 2));
    }

    private static boolean[] disc(int size, double radius) {
        boolean[] opaque = new boolean[size * size];
        double c = size / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x + 0.5 - c;
                double dy = y + 0.5 - c;
                opaque[y * size + x] = dx * dx + dy * dy <= radius * radius;
            }
        }
        return opaque;
    }

    private static int count(boolean[] cells) {
        int n = 0;
        for (boolean c : cells) {
            if (c) {
                n++;
            }
        }
        return n;
    }
}
