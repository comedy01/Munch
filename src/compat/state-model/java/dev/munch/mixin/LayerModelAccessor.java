package dev.munch.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerModelAccessor {
    @Accessor("model")
    BakedModel munch$model();

    @Accessor("model")
    void munch$setModel(BakedModel model);

    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> munch$specialRenderer();
}
