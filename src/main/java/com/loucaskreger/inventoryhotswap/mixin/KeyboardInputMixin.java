package com.loucaskreger.inventoryhotswap.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.loucaskreger.inventoryhotswap.InventoryHotswap;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

@Mixin(KeyboardHandler.class)
public class KeyboardInputMixin {
    @Inject(method = "keyPress", cancellable = true, at = @At("HEAD"))
    private void onKeyboardInput(long windowPointer, int action, KeyEvent keyEvent, CallbackInfo ci) {
        if (action != InputConstants.PRESS && action != InputConstants.REPEAT) {
            return;
        }

        if (InventoryHotswap.handleHotbarSelectionKey(keyEvent)) {
            ci.cancel();
        }
    }
}
