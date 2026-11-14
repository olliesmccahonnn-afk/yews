# Item Skins (Fabric, Minecraft Java 1.21.11)

Import PNG item textures from inside the game and use them on any item. No resource pack to enable yourself.

## Build (GitHub, no installs)

1. Create a free repo on github.com and upload everything in this folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab. The build runs on every upload (about 3 minutes); you can also press *Run workflow*.
3. Open the finished run, download the **itemskins-jar** artifact, unzip it, and put the `.jar` in `.minecraft/mods`
   together with Fabric API.

## Use

- **K** opens the menu in-world (rebind under Controls > Item Skins).
- A second key, **Next skin preset** (unbound by default), cycles presets without opening the menu.
- **Skins** tab: paste a PNG path and click *Import*, or drag & drop PNGs (or a folder) onto the window.
  You can also drop PNGs straight into `config/itemskins/skins/`.
  Select a skin, then *Assign to item...* to pick which item should use it.
- **Presets** tab: create named presets, then *Add item* (search any item) or *Held item*, and pick a skin.
  Click an entry to change its skin, `x` to remove it. *Activate* makes a preset the active one.
- **Tabs**: on both pages, type a name and click *+ Tab*. *Tab: ...* on the selected skin/preset moves it
  between tabs, *- Tab* deletes the current tab (entries stay under *All*). Mouse wheel scrolls lists.
- Changes apply with **Apply** (or automatically when you close the menu). *Mod: ON/OFF* switches every skin off.

## How it works / caveats

- The mod writes a pack to `resourcepacks/itemskins_generated` and enables it on top automatically.
  Leave it enabled; it is regenerated whenever you apply.
- Applying does a normal resource reload (a second or two, same as F3+T).
- A skinned item's whole item definition is replaced by a flat model with your texture, so items with
  special states (bow pulling, compass, clock, shield, trident...) show your texture but lose those states.
- If the generated pack shows as "incompatible", adjust `PACK_FORMAT` in `PackBuilder.java`
  (75 is the 1.21.11 resource pack format).
- If a compile error points at a Yarn name, the culprits to check first are:
  `KeyBinding.Category.create`, `DrawContext.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ...)`,
  `Screen.onFilesDropped`, `ResourcePackManager.setEnabledProfiles`, `GameOptions.refreshResourcePacks`.
