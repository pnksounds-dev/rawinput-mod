# Raw Input — documentation

## What this mod does

Minecraft 26.3 replaced GLFW with **SDL3** for windowing and input, and removed the
*Raw Input* mouse setting. In-game the client now always uses SDL *relative mouse
mode* and turns the camera using `SDL_MouseMotionEvent.xrel/yrel` deltas
(`InputConstants.grabMouse` → `SDL_SetWindowRelativeMouseMode`, deltas delivered
via `SDLEventHandler` → `MouseHandler.onMove` → `accumulatedDX/DY` → `turnPlayer`).

This mod restores guaranteed raw input by pinning the SDL behaviour Mojang left
at defaults:

| Mechanism | Effect |
|---|---|
| `SDL_MOUSE_RELATIVE_SYSTEM_SCALE` reset to default (`0`) | SDL does **not** apply the Windows mouse speed/acceleration curve to relative deltas |
| `SDL_MOUSE_RELATIVE_SPEED_SCALE` reset to default | No extra SDL speed scaling |
| `SDL_SetRelativeMouseTransform(identity)` | Overrides *all* SDL-side transforms of relative motion — the strongest guarantee of 1:1 raw deltas |

The transform callback leaves `*x`/`*y` untouched, so every relative delta SDL
reports reaches the camera unmodified — i.e. raw input.

## Where it hooks

- `RawInputMod` (client entrypoint) applies the settings once at startup and
  writes `config/rawinput.json` on first launch.
- `InputConstantsMixin` re-applies them at the HEAD of
  `InputConstants.grabMouse(Window, double, double)`, right before SDL relative
  mode is (re)enabled. `SDL_SetRelativeMouseTransform` *requires* relative mode
  to be off when called, so this ordering matters — it also acts as a retry if
  SDL was not ready during init, or if another mod touched the hints.

## Honest note on vanilla 26.3 behaviour

SDL3 relative mouse mode on Windows already reads `WM_INPUT` (the Windows raw
input device) and `SDL_MOUSE_RELATIVE_SYSTEM_SCALE` defaults to `0` (unscaled).
Mojira MC-312357 confirms the game now ignores Windows mouse settings entirely.

So vanilla 26.3 in-game look is *already* effectively raw — what Mojang removed
is the **toggle**. This mod (a) pins the raw behaviour explicitly so nothing can
reintroduce scaling, and (b) restores the off-switch: set `"rawInput": false` in
`config/rawinput.json` and SDL applies the system mouse curve, approximating the
old "raw input off" mode (useful for trackpad users or players who relied on
Windows acceleration).

If the mouse *still* feels wrong, likely external culprits (see Mojira
MC-312241): X-Mouse Button Control or similar mouse utilities break SDL3's
relative mode — close them before blaming the game.

## Config

`config/rawinput.json` (created automatically on first launch):

```json
{
    "rawInput": true
}
```

- `true` (default): raw, unaccelerated 1:1 mouse motion in-game.
- `false`: SDL applies the OS mouse curve (legacy non-raw feel). Restart required.

## Compatibility

- Requires: Minecraft `>=26.3`, Fabric Loader `>=0.19.0`, Java `>=21`.
- No Fabric API dependency.
- Client-only; safe on servers.
- Compatible with Ixeris (separate SDL event polling thread — we only set
  hints/transform, which are thread-safe SDL calls), Controlify, Mouse Tweaks,
  and the rest of Fabulously Optimized.

## Building

Requires a JDK (17+ to run Gradle; the build downloads nothing extra beyond Loom
deps). The workspace targets JDK 25 since MC 26.3 is class-file v69, but the mod
itself compiles with `--release 21`.

```bash
gradlew build
```

Output: `build/libs/rawinput-1.0.0.jar` — drop it in your `mods` folder.

Key build files:

- `build.gradle` — uses `net.fabricmc.fabric-loom` 1.17.21 (the **unobfuscated**
  variant; the legacy `fabric-loom`/`fabric-loom-remap` plugin is for MC <= 1.21.11).
  No `mappings` block — 26.x ships unobfuscated (Mojang names), and
  `loom.officialMojangMappings()` fails because Mojang no longer publishes mapping
  files. Uses plain `implementation` instead of `modImplementation`.
- `gradle.properties` — `minecraft_version=26.3`, `loader_version=0.19.5`.

## Changelog

### 1.0.0 — 2026-10-12

- Initial release: SDL hint pinning + identity relative-mouse transform,
  `grabMouse` re-assert mixin, `rawinput.json` config.
