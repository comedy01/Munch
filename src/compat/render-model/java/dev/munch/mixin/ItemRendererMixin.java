package dev.munch.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.munch.client.Hook;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    @ModifyArg(
            method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"),
            index = 7)
    private BakedModel munch$bite(BakedModel model, @Local(argsOnly = true) LivingEntity entity,
                                  @Local(argsOnly = true) ItemStack stack,
                                  @Local(argsOnly = true) ItemDisplayContext context,
                                  @Local(argsOnly = true) Level level) {
        return Hook.model((ItemRenderer) (Object) this, model, stack, context, entity, level);
    }
}
