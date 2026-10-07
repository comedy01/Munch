package dev.munch.mixin;

import dev.munch.client.Hook;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void munch$bite(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
                            boolean leftHand, Level level, LivingEntity owner, int seed, CallbackInfo ci) {
        ItemModelResolver resolver = (ItemModelResolver) (Object) this;
        Hook.apply(output, item, displayContext, owner,
                (state, stack) -> resolver.updateForTopItem(state, stack, displayContext, leftHand, level, owner, seed));
    }
}
