package dev.munch.mixin;

import dev.munch.client.Hook;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
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
                            Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        Hook.apply((ItemModelResolver) (Object) this, output, item, displayContext, level, owner, seed);
    }
}
