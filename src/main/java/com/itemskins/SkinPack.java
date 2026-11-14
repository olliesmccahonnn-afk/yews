package com.itemskins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.util.ArrayList;
import java.util.List;

public final class SkinPack {
    private SkinPack() {}

    /** Rebuilds the generated pack, makes sure it is enabled on top, and reloads resources. */
    public static void apply(MinecraftClient mc) {
        PackBuilder.rebuild();
        ensureEnabled(mc);
        mc.reloadResources();
    }

    /** @return true if the enabled-pack list was changed (a reload is then needed). */
    public static boolean ensureEnabled(MinecraftClient mc) {
        ResourcePackManager mgr = mc.getResourcePackManager();
        mgr.scanPacks();

        List<String> enabled = new ArrayList<>(mgr.getEnabledIds());
        if (!enabled.isEmpty() && enabled.get(enabled.size() - 1).equals(PackBuilder.PACK_ID)) {
            return false; // already enabled and on top
        }
        enabled.remove(PackBuilder.PACK_ID);
        enabled.add(PackBuilder.PACK_ID); // last = highest priority
        mgr.setEnabledProfiles(enabled);
        mc.options.refreshResourcePacks(mgr);
        return true;
    }
}
