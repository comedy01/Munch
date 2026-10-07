package dev.munch.client;

import net.minecraft.client.model.geom.builders.UVPair;
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
            Vector3f[] positions = new Vector3f[4];
            float[] u = new float[4];
            float[] v = new float[4];
            for (int i = 0; i < 4; i++) {
                positions[i] = new Vector3f(quad.position(i));
                u[i] = UVPair.unpackU(quad.packedUV(i));
                v[i] = UVPair.unpackV(quad.packedUV(i));
            }
            out.add(new Quad(positions, u, v, quad.sprite(), quad.direction(), quad));
        }
        return out;
    }

    static List<BakedQuad> write(List<Quad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (Quad quad : quads) {
            BakedQuad source = (BakedQuad) quad.source();
            out.add(new BakedQuad(quad.position(0), quad.position(1), quad.position(2), quad.position(3),
                    UVPair.pack(quad.u(0), quad.v(0)), UVPair.pack(quad.u(1), quad.v(1)),
                    UVPair.pack(quad.u(2), quad.v(2)), UVPair.pack(quad.u(3), quad.v(3)),
                    source.tintIndex(), quad.direction(), source.sprite(), source.shade(), source.lightEmission()));
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
