package com.gambletracker.mixin;

import com.gambletracker.GambleMod;
import com.gambletracker.ZzMenu;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void gambletracker$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (ZzMenu.onMouse(info.button(), action) || GambleMod.onMouse(info.button(), action)) {
            ci.cancel();
        }
    }
}
