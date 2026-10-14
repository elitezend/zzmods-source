package com.gambletracker.mixin;

import com.gambletracker.GambleState;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.21.11: here the boolean really is "overlay" (action bar), and those never count as a payment
@Mixin(ChatListener.class)
public class ChatListenerMixin {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void gambletracker$onSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (!overlay) {
            GambleState.INSTANCE.onServerMessage(message.getString());
        }
    }
}
