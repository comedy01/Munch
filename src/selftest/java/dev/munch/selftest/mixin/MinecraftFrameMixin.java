package dev.munch.selftest.mixin;

import dev.munch.selftest.MunchSelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void munchSelftest$frame(boolean advanceGameTime, CallbackInfo ci) {
        MunchSelfTest.frame();
    }
}
