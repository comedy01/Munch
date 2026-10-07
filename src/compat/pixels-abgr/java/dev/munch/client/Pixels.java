package dev.munch.client;

import com.mojang.blaze3d.platform.NativeImage;

final class Pixels {
    private Pixels() {
    }

    static int argb(NativeImage image, int x, int y) {
        int abgr = image.getPixelRGBA(x, y);
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
    }
}
