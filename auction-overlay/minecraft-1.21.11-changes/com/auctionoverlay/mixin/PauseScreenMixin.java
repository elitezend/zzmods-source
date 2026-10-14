package com.auctionoverlay.mixin;

import com.auctionoverlay.ZzMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.21.11 (PauseScreen.render). The zzmods section of the game menu (Esc): our logo and the Announcements button, lined up with Essential's buttons
// on the right (or the middle right without Essential) - drawn by ZzMenu; its click comes from MouseHandlerMixin.
// Nothing is overridden, so Auction Overlay and Gamble Tracker can both carry it - only the one that claims it shows it.
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void auctionoverlay$zzmods(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (((PauseScreen) (Object) this).showsPauseMenu() && ZzMenu.show()) ZzMenu.render(g, font, this, mouseX, mouseY);
    }
}
