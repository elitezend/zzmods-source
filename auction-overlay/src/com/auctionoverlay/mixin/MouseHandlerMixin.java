package com.auctionoverlay.mixin;

import com.auctionoverlay.ZzMenu;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the Announcements button in the game menu: its click is taken here, before Essential's menu layer can swallow it
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void auctionoverlay$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (ZzMenu.onMouse(info.button(), action)) {
            ci.cancel();
        }
    }
}
