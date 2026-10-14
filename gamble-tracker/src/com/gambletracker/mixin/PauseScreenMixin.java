package com.gambletracker.mixin;

import com.gambletracker.ZzMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the zzmods section of the game menu (Esc): our logo and the Announcements button, lined up with Essential's buttons
// on the right (or the middle right without Essential) - drawn by ZzMenu; its click comes from MouseHandlerMixin.
// Nothing is overridden, so Auction Overlay and Gamble Tracker can both carry it - only the one that claims it shows it.
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void gambletracker$zzmods(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (((PauseScreen) (Object) this).showsPauseMenu() && ZzMenu.show()) ZzMenu.render(g, font, this, mouseX, mouseY);
    }
}
