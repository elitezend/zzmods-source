package com.auctionoverlay.mixin;

import com.auctionoverlay.AuctionHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.11: the HUD is drawn by Gui.render (26.x moved it to Hud.extractRenderState). */
@Mixin(Gui.class)
public class HudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void auctionoverlay$renderCard(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        AuctionHud.render(graphics);
    }
}
