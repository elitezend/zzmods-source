package com.gambletracker.mixin;

import com.gambletracker.GambleState;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// system messages only (never signed player chat). Careful: in 26.2 that boolean is not "overlay",
// normal server chat comes through with it set to true, so it can't be used as a filter.
@Mixin(ChatListener.class)
public class ChatListenerMixin {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void gambletracker$onSystemMessage(Component message, boolean flag, CallbackInfo ci) {
        GambleState.INSTANCE.onServerMessage(message.getString());
    }
}
