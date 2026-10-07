package dev.munch.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @SuppressWarnings("rawtypes")
    @Accessor("quads")
    List munch$quads();

    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> munch$specialRenderer();
}
