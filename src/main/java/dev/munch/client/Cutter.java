package dev.munch.client;

import dev.munch.core.Plan;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class Cutter {
    private static final float EPS = 0.02F;
    private static final float INSET = 0.1F;
    private static final float EDGE = 0.05F;

    private Cutter() {
    }

    static List<TextureAtlasSprite> sprites(List<Quad> quads) {
        List<TextureAtlasSprite> sprites = new ArrayList<>(2);
        for (Quad quad : quads) {
            if (!sprites.contains(quad.sprite)) {
                sprites.add(quad.sprite);
            }
        }
        return sprites;
    }

    static List<Quad> cut(List<Quad> quads, Plan plan, int stage, boolean inside) {
        List<Quad> out = new ArrayList<>();
        for (TextureAtlasSprite sprite : sprites(quads)) {
            List<Quad> group = new ArrayList<>();
            for (Quad quad : quads) {
                if (quad.sprite == sprite) {
                    group.add(quad);
                }
            }
            if (!inside && !plan.targets(sprite)) {
                out.addAll(group);
                continue;
            }
            List<Quad> cut = cutLayer(group, sprite, plan, stage, inside);
            if (cut == null) {
                return null;
            }
            out.addAll(cut);
        }
        return out;
    }

    private static List<Quad> cutLayer(List<Quad> quads, TextureAtlasSprite sprite, Plan plan, int stage, boolean inside) {
        SpriteContents contents = sprite.contents();
        int width = contents.width();
        int height = contents.height();

        List<Face> faces = new ArrayList<>(2);
        for (Quad quad : quads) {
            Face face = Face.of(quad, width, height);
            if (face != null) {
                faces.add(face);
            }
        }
        if (faces.size() != 2) {
            return null;
        }

        boolean[] solid = new boolean[width * height];
        boolean[] kept = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                solid[i] = !contents.isTransparent(0, x, y);
                kept[i] = solid[i] && plan.removed(x, y, width, height, stage) == inside;
            }
        }

        List<Quad> out = new ArrayList<>();
        for (Face face : faces) {
            for (int y = 0; y < height; y++) {
                int x = 0;
                while (x < width) {
                    if (!kept[y * width + x]) {
                        x++;
                        continue;
                    }
                    int start = x;
                    while (x < width && kept[y * width + x]) {
                        x++;
                    }
                    out.add(face.part(start, y, x, y + 1));
                }
            }
        }

        Face front = faces.get(0);
        Face back = faces.get(1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!kept[y * width + x]) {
                    continue;
                }
                if (edge(solid, kept, plan, inside, width, height, x + 1, y)) {
                    out.add(wall(front, back, width, height, x, y, x + 1, y, x + 1, y + 1, 1, 0));
                }
                if (edge(solid, kept, plan, inside, width, height, x - 1, y)) {
                    out.add(wall(front, back, width, height, x, y, x, y + 1, x, y, -1, 0));
                }
                if (edge(solid, kept, plan, inside, width, height, x, y + 1)) {
                    out.add(wall(front, back, width, height, x, y, x + 1, y + 1, x, y + 1, 0, 1));
                }
                if (edge(solid, kept, plan, inside, width, height, x, y - 1)) {
                    out.add(wall(front, back, width, height, x, y, x, y, x + 1, y, 0, -1));
                }
            }
        }
        return out;
    }

    private static boolean edge(boolean[] solid, boolean[] kept, Plan plan, boolean inside, int width, int height,
                                int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return true;
        }
        int i = y * width + x;
        if (!solid[i]) {
            return true;
        }
        if (kept[i]) {
            return false;
        }
        return !inside && !plan.covered(x, y, width, height);
    }

    private static Quad wall(Face front, Face back, int width, int height,
                             int px, int py, float s0, float t0, float s1, float t1, int dx, int dy) {
        Vector3f a = front.at(s0, t0);
        Vector3f b = front.at(s1, t1);
        Vector3f c = back.at(s1, t1);
        Vector3f d = back.at(s0, t0);
        Vector3f out = front.at(px + 0.5F + dx, py + 0.5F + dy).sub(front.at(px + 0.5F, py + 0.5F));
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.dot(out) < 0) {
            Vector3f swap = b;
            b = d;
            d = swap;
        }
        TextureAtlasSprite sprite = front.quad.sprite;
        float u0 = spriteU(sprite, (px + INSET) / width);
        float u1 = spriteU(sprite, (px + 1 - INSET) / width);
        float v0 = spriteV(sprite, (py + INSET) / height);
        float v1 = spriteV(sprite, (py + 1 - INSET) / height);
        return front.quad.with(new Vector3f[]{a, b, c, d},
                new float[]{u0, u0, u1, u1}, new float[]{v0, v1, v1, v0}, nearest(out));
    }

    static Direction nearest(Vector3f vector) {
        float ax = Math.abs(vector.x());
        float ay = Math.abs(vector.y());
        float az = Math.abs(vector.z());
        if (ax >= ay && ax >= az) {
            return vector.x() >= 0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= az) {
            return vector.y() >= 0 ? Direction.UP : Direction.DOWN;
        }
        return vector.z() >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static float spriteS(TextureAtlasSprite sprite, float u, int width) {
        return (u - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * width;
    }

    private static float spriteT(TextureAtlasSprite sprite, float v, int height) {
        return (v - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * height;
    }

    private static float spriteU(TextureAtlasSprite sprite, float fraction) {
        return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * fraction;
    }

    private static float spriteV(TextureAtlasSprite sprite, float fraction) {
        return sprite.getV0() + (sprite.getV1() - sprite.getV0()) * fraction;
    }

    private static final class Face {
        private final Quad quad;
        private final int width;
        private final int height;
        private final float[] s;
        private final float[] t;
        private final Vector3f origin;
        private final Vector3f alongS;
        private final Vector3f alongT;

        private Face(Quad quad, int width, int height, float[] s, float[] t, Vector3f alongS, Vector3f alongT) {
            this.quad = quad;
            this.width = width;
            this.height = height;
            this.s = s;
            this.t = t;
            this.origin = new Vector3f(quad.positions[0]);
            this.alongS = alongS;
            this.alongT = alongT;
        }

        static Face of(Quad quad, int width, int height) {
            float minS = Float.MAX_VALUE;
            float maxS = -Float.MAX_VALUE;
            float minT = Float.MAX_VALUE;
            float maxT = -Float.MAX_VALUE;
            float[] s = new float[4];
            float[] t = new float[4];
            for (int i = 0; i < 4; i++) {
                s[i] = spriteS(quad.sprite, quad.u[i], width);
                t[i] = spriteT(quad.sprite, quad.v[i], height);
                minS = Math.min(minS, s[i]);
                maxS = Math.max(maxS, s[i]);
                minT = Math.min(minT, t[i]);
                maxT = Math.max(maxT, t[i]);
            }
            if (Math.abs(minS) > EPS * width || Math.abs(maxS - width) > EPS * width
                    || Math.abs(minT) > EPS * height || Math.abs(maxT - height) > EPS * height) {
                return null;
            }
            Vector3f p0 = quad.positions[0];
            Vector3f d1 = new Vector3f(quad.positions[1]).sub(p0);
            Vector3f d2 = new Vector3f(quad.positions[2]).sub(p0);
            float ds1 = s[1] - s[0];
            float dt1 = t[1] - t[0];
            float ds2 = s[2] - s[0];
            float dt2 = t[2] - t[0];
            float det = ds1 * dt2 - ds2 * dt1;
            if (Math.abs(det) < 1.0E-6F) {
                return null;
            }
            Vector3f alongS = new Vector3f(d1).mul(dt2).sub(new Vector3f(d2).mul(dt1)).div(det);
            Vector3f alongT = new Vector3f(d2).mul(ds1).sub(new Vector3f(d1).mul(ds2)).div(det);
            return new Face(quad, width, height, s, t, alongS, alongT);
        }

        Vector3f at(float ps, float pt) {
            return new Vector3f(origin)
                    .add(new Vector3f(alongS).mul(ps - s[0]))
                    .add(new Vector3f(alongT).mul(pt - t[0]));
        }

        Quad part(int x0, int y0, int x1, int y1) {
            Vector3f[] p = new Vector3f[4];
            float[] u = new float[4];
            float[] v = new float[4];
            for (int i = 0; i < 4; i++) {
                float ps = s[i] < width / 2.0F ? x0 : x1;
                float pt = t[i] < height / 2.0F ? y0 : y1;
                p[i] = at(ps, pt);
                float us = ps == x0 ? ps + EDGE : ps - EDGE;
                float vt = pt == y0 ? pt + EDGE : pt - EDGE;
                u[i] = spriteU(quad.sprite, us / width);
                v[i] = spriteV(quad.sprite, vt / height);
            }
            return quad.with(p, u, v, quad.direction);
        }
    }
}
