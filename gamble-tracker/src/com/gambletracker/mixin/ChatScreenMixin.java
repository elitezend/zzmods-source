package com.gambletracker.mixin;

import com.gambletracker.GambleHud;
import com.gambletracker.GambleMod;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// with chat open the mouse is free, so the gear on the card can be clicked
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void gambletracker$clickGear(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == 0 && GambleHud.gearContains(event.x(), event.y())) {
            GambleMod.openPanel();
            cir.setReturnValue(true);
        }
    }
}
