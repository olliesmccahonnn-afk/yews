package com.itemskins;

import com.itemskins.gui.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ItemSkinsClient implements ClientModInitializer {
    public static final String MOD_ID = "itemskins";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openKey;
    private static KeyBinding cycleKey;
    private static boolean bootChecked;

    @Override
    public void onInitializeClient() {
        SkinStore.load();
        PackBuilder.rebuild(); // make sure the pack exists before Minecraft scans resource packs

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.itemskins.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        cycleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.itemskins.cycle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientTickEvents.END_CLIENT_TICK.register(ItemSkinsClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        // One-time: once the game has finished starting, make sure our generated pack is enabled.
        if (!bootChecked && mc.getOverlay() == null) {
            bootChecked = true;
            if (SkinPack.ensureEnabled(mc)) mc.reloadResources();
        }

        while (openKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new MainScreen(null));
        }

        while (cycleKey.wasPressed()) {
            cyclePreset(mc);
        }
    }

    private static void cyclePreset(MinecraftClient mc) {
        List<SkinStore.Preset> presets = SkinStore.data.presets;
        if (presets.isEmpty()) return;

        int idx = -1;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name.equalsIgnoreCase(SkinStore.data.activePreset)) idx = i;
        }
        SkinStore.Preset next = presets.get((idx + 1) % presets.size());
        SkinStore.data.activePreset = next.name;
        SkinStore.save();

        if (mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(Text.literal("Item Skins preset: " + next.name), false);
        }
        SkinPack.apply(mc);
    }
}
