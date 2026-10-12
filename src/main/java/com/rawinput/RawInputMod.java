package com.rawinput;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDL_MouseMotionTransformCallbackI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Restores the raw mouse input behaviour removed in Minecraft 26.3.
 *
 * <p>Since 26.3 (SDL3 input backend) Minecraft enables SDL relative mouse mode
 * while playing and always feeds {@code SDL_MouseMotionEvent.xrel/yrel} into
 * the camera. This mod forces those deltas to be raw and unaccelerated by:
 * <ul>
 *   <li>pinning {@code SDL_MOUSE_RELATIVE_SYSTEM_SCALE} / {@code SDL_MOUSE_RELATIVE_SPEED_SCALE}
 *       so SDL never applies the OS mouse speed/acceleration curve, and</li>
 *   <li>installing an identity {@code SDL_SetRelativeMouseTransform} callback, which
 *       overrides all SDL-side scaling of relative motion entirely.</li>
 * </ul>
 *
 * <p>Set {@code "rawInput": false} in {@code config/rawinput.json} to get the old
 * non-raw behaviour back (SDL applies the system mouse curve, like pre-26.3 with
 * raw input off).
 */
public class RawInputMod implements ClientModInitializer {
	public static final String MOD_ID = "rawinput";
	public static final Logger LOGGER = LoggerFactory.getLogger("RawInput");

	private static final String HINT_RELATIVE_SYSTEM_SCALE = "SDL_MOUSE_RELATIVE_SYSTEM_SCALE";
	private static final String HINT_RELATIVE_SPEED_SCALE = "SDL_MOUSE_RELATIVE_SPEED_SCALE";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static boolean rawInputEnabled = true;
	// Strong reference required so the native callback trampoline is not GC'd.
	private static SDL_MouseMotionTransformCallbackI rawMotionTransform;
	private static boolean transformInstalled;
	private static boolean appliedOnce;

	@Override
	public void onInitializeClient() {
		rawInputEnabled = loadConfig();
		apply();
		LOGGER.info("[RawInput] Loaded: raw mouse input {}.", rawInputEnabled ? "enabled" : "disabled (system mouse curve active)");
	}

	/**
	 * Idempotent. Called once at client init and again before every mouse grab
	 * via {@code InputConstantsMixin}, in case SDL was not ready yet or another
	 * mod touched the hints/transform.
	 */
	public static void apply() {
		try {
			if (rawInputEnabled) {
				// Default SDL behaviour is already unscaled, but pin it explicitly.
				SDLHints.SDL_ResetHint(HINT_RELATIVE_SYSTEM_SCALE);
				SDLHints.SDL_ResetHint(HINT_RELATIVE_SPEED_SCALE);
				installTransform();
			} else {
				removeTransform();
				SDLHints.SDL_SetHint(HINT_RELATIVE_SYSTEM_SCALE, "1");
			}
			if (!appliedOnce) {
				appliedOnce = true;
				LOGGER.info("[RawInput] Applied {} relative mouse motion.", rawInputEnabled ? "raw (unscaled 1:1)" : "system-scaled");
			}
		} catch (Throwable t) {
			LOGGER.warn("[RawInput] Failed to apply mouse settings, will retry on next mouse grab", t);
		}
	}

	private static void installTransform() {
		if (transformInstalled) {
			return;
		}
		if (rawMotionTransform == null) {
			// Identity transform: leaves the relative *x/*y untouched -> raw 1:1 deltas.
			rawMotionTransform = (userdata, timestamp, window, mouseID, x, y) -> {
			};
		}
		if (SDLMouse.SDL_SetRelativeMouseTransform(rawMotionTransform, 0L)) {
			transformInstalled = true;
		} else {
			LOGGER.warn("[RawInput] SDL_SetRelativeMouseTransform failed: {}", SDLError.SDL_GetError());
		}
	}

	private static void removeTransform() {
		if (!transformInstalled) {
			return;
		}
		if (SDLMouse.nSDL_SetRelativeMouseTransform(0L, 0L)) {
			transformInstalled = false;
		} else {
			LOGGER.warn("[RawInput] Failed to remove relative mouse transform: {}", SDLError.SDL_GetError());
		}
	}

	private static boolean loadConfig() {
		Path configFile = FabricLoader.getInstance().getConfigDir().resolve("rawinput.json");
		if (Files.exists(configFile)) {
			try (Reader reader = Files.newBufferedReader(configFile)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("rawInput")) {
					return json.get("rawInput").getAsBoolean();
				}
			} catch (Throwable t) {
				LOGGER.warn("[RawInput] Could not read config, defaulting to raw input enabled", t);
			}
			return true;
		}

		JsonObject json = new JsonObject();
		json.addProperty("rawInput", true);
		try (Writer writer = Files.newBufferedWriter(configFile)) {
			GSON.toJson(json, writer);
		} catch (IOException e) {
			LOGGER.warn("[RawInput] Could not write default config", e);
		}
		return true;
	}
}
