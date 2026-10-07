package dev.munch.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class Quads {
    private Quads() {
    }

    static List<Quad> read(List<?> quads) {
        List<Quad> out = new ArrayList<>(quads.size());
        for (Object raw : quads) {
            BakedQuad quad = (BakedQuad) raw;
            int[] data = quad.vertices();
            int stride = data.length / 4;
            Vector3f[] positions = new Vector3f[4];
            float[] u = new float[4];
            float[] v = new float[4];
            for (int i = 0; i < 4; i++) {
                int at = i * stride;
                positions[i] = new Vector3f(Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]),
                        Float.intBitsToFloat(data[at + 2]));
                u[i] = Float.intBitsToFloat(data[at + 4]);
                v[i] = Float.intBitsToFloat(data[at + 5]);
            }
            out.add(new Quad(positions, u, v, quad.sprite(), quad.direction(), quad));
        }
        return out;
    }

    static List<BakedQuad> write(List<Quad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (Quad quad : quads) {
            BakedQuad source = (BakedQuad) quad.source();
            int[] data = source.vertices().clone();
            int stride = data.length / 4;
            for (int i = 0; i < 4; i++) {
                int at = i * stride;
                data[at] = Float.floatToRawIntBits(quad.position(i).x());
                data[at + 1] = Float.floatToRawIntBits(quad.position(i).y());
                data[at + 2] = Float.floatToRawIntBits(quad.position(i).z());
                data[at + 4] = Float.floatToRawIntBits(quad.u(i));
                data[at + 5] = Float.floatToRawIntBits(quad.v(i));
            }
            out.add(new BakedQuad(data, source.tintIndex(), quad.direction(), source.sprite(), source.shade(),
                    source.lightEmission()));
        }
        return out;
    }

    static TextureAtlasSprite sprite(Object quad) {
        return ((BakedQuad) quad).sprite();
    }

    static int tintIndex(Object quad) {
        return ((BakedQuad) quad).tintIndex();
    }
}
