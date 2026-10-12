package com.rawinput.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.rawinput.RawInputMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {

	/**
	 * Re-assert raw input settings right before SDL relative mouse mode is
	 * (re)enabled. This guarantees the identity motion transform is installed
	 * while relative mode is still off, which SDL requires for
	 * {@code SDL_SetRelativeMouseTransform} to succeed.
	 */
	@Inject(method = "grabMouse", at = @At("HEAD"))
	private static void rawinput$applyBeforeGrab(Window window, double xpos, double ypos, CallbackInfo ci) {
		RawInputMod.apply();
	}
}
