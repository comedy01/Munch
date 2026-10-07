package dev.munch.client;

import com.mojang.blaze3d.platform.NativeImage;

final class Pixels {
    private Pixels() {
    }

    static int argb(NativeImage image, int x, int y) {
        return image.getPixel(x, y);
    }
}
