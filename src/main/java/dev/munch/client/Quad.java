package dev.munch.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public final class Quad {
    final Vector3f[] positions;
    final float[] u;
    final float[] v;
    final TextureAtlasSprite sprite;
    final Direction direction;
    final Object source;

    public Quad(Vector3f[] positions, float[] u, float[] v, TextureAtlasSprite sprite, Direction direction, Object source) {
        this.positions = positions;
        this.u = u;
        this.v = v;
        this.sprite = sprite;
        this.direction = direction;
        this.source = source;
    }

    public Vector3f position(int vertex) {
        return positions[vertex];
    }

    public float u(int vertex) {
        return u[vertex];
    }

    public float v(int vertex) {
        return v[vertex];
    }

    public TextureAtlasSprite sprite() {
        return sprite;
    }

    public Direction direction() {
        return direction;
    }

    public Object source() {
        return source;
    }

    Quad with(Vector3f[] positions, float[] u, float[] v, Direction direction) {
        return new Quad(positions, u, v, sprite, direction, source);
    }
}
