package com.loucaskreger.inventoryhotswap.mixin;

import static org.lwjgl.glfw.GLFW.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.loucaskreger.inventoryhotswap.InventoryHotswap;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

@Mixin(KeyboardHandler.class)
public class KeyboardInputMixin {
    @Inject(method = "keyPress", cancellable = true, at = @At("HEAD"))
    private void onKeyboardInput(long windowPointer, int action, KeyEvent keyEvent, CallbackInfo ci) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) {
            return;
        }

        if (InventoryHotswap.handleHotbarSelectionKey(keyEvent)) {
            ci.cancel();
        }
    }
}
