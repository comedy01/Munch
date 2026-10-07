package dev.munch.client;

import dev.munch.mixin.LayerTintsAccessor;
import net.minecraft.client.renderer.item.ItemStackRenderState;

final class Tints {
    private Tints() {
    }

    static boolean tinted(ItemStackRenderState.LayerRenderState layer, int index) {
        int[] tints = ((LayerTintsAccessor) layer).munch$tintLayers();
        return tints != null && index >= 0 && index < tints.length && tints[index] != -1;
    }
}
