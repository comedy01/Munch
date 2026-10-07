package dev.munch.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.item.ItemStackRenderState;

final class Tints {
    private Tints() {
    }

    static boolean tinted(ItemStackRenderState.LayerRenderState layer, int index) {
        IntList tints = layer.tintLayers();
        return index >= 0 && index < tints.size() && tints.getInt(index) != -1;
    }
}
