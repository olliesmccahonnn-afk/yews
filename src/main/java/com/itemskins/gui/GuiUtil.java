package com.itemskins.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class GuiUtil {
    private GuiUtil() {}

    static ButtonWidget btn(String text, int x, int y, int w, int h, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, w, h).build();
    }

    /** Draws an imported skin from the GUI sprite atlas (exists once the generated pack has been loaded). */
    static void drawSkin(DrawContext ctx, String skinId, int x, int y, int size) {
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("itemskins", "skin/" + skinId), x, y, size, size);
    }

    static Item item(String id) {
        Identifier i = Identifier.tryParse(id);
        if (i == null || !Registries.ITEM.containsId(i)) return null;
        return Registries.ITEM.get(i);
    }

    static String id(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    static String name(Item item) {
        return new ItemStack(item).getName().getString();
    }

    static String name(String itemId) {
        Item it = item(itemId);
        return it == null ? itemId : name(it);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int pages(int count, int perPage) {
        return Math.max(1, (count + perPage - 1) / perPage);
    }
}
