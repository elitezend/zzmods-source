package com.auctionoverlay.mixin;

import com.auctionoverlay.PaymentDetector;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.11: action-bar messages also arrive through handleSystemMessage (overlay = true). */
@Mixin(ChatListener.class)
public class ChatListenerMixin {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void auctionoverlay$onSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        PaymentDetector.onServerMessage(message.getString());
    }
}
