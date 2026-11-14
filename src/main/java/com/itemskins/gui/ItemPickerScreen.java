package com.itemskins.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.itemskins.gui.GuiUtil.btn;

/** Pick any registered item from a searchable icon grid. */
public class ItemPickerScreen extends Screen {
    private static final int CELL = 22;

    private final Screen parent;
    private final Consumer<Item> onPick;
    private final List<Item> all = new ArrayList<>();
    private final List<ClickableWidget> gridWidgets = new ArrayList<>();
    private final List<Consumer<DrawContext>> overlays = new ArrayList<>();

    private List<Item> filtered = new ArrayList<>();
    private String query = "";
    private int page;
    private int perPage = 1;
    private int totalPages = 1;
    private ButtonWidget prev, next;

    public ItemPickerScreen(Screen parent, Consumer<Item> onPick) {
        super(Text.literal("Pick an item"));
        this.parent = parent;
        this.onPick = onPick;
        for (Item item : Registries.ITEM) {
            if (item != Items.AIR) all.add(item);
        }
    }

    @Override
    protected void init() {
        gridWidgets.clear();

        TextFieldWidget search = new TextFieldWidget(textRenderer, (width - 220) / 2, 10, 220, 20, Text.empty());
        search.setMaxLength(64);
        search.setPlaceholder(Text.literal("Search items..."));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            page = 0;
            rebuildGrid();
        });
        addDrawableChild(search);
        setInitialFocus(search);

        addDrawableChild(btn("Back", 8, height - 28, 60, 20, b -> close()));
        prev = btn("<", width / 2 - 50, height - 28, 20, 20, b -> {
            page--;
            rebuildGrid();
        });
        next = btn(">", width / 2 + 30, height - 28, 20, 20, b -> {
            page++;
            rebuildGrid();
        });
        addDrawableChild(prev);
        addDrawableChild(next);

        rebuildGrid();
    }

    private void rebuildGrid() {
        for (ClickableWidget w : gridWidgets) remove(w);
        gridWidgets.clear();
        overlays.clear();

        String q = query.trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (Item item : all) {
            if (q.isEmpty()
                    || GuiUtil.id(item).contains(q)
                    || GuiUtil.name(item).toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(item);
            }
        }

        int cols = Math.max(1, Math.min(16, (width - 24) / CELL));
        int top = 38;
        int bottom = height - 36;
        int rows = Math.max(1, (bottom - top) / CELL);
        perPage = cols * rows;
        totalPages = GuiUtil.pages(filtered.size(), perPage);
        page = GuiUtil.clamp(page, 0, totalPages - 1);

        int left = (width - cols * CELL) / 2;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < filtered.size(); i++) {
            Item item = filtered.get(start + i);
            int x = left + (i % cols) * CELL;
            int y = top + (i / cols) * CELL;
            ButtonWidget b = btn("", x, y, 20, 20, bb -> {
                client.setScreen(parent);
                onPick.accept(item);
            });
            b.setTooltip(Tooltip.of(Text.literal(GuiUtil.name(item) + "\n" + GuiUtil.id(item))));
            addDrawableChild(b);
            gridWidgets.add(b);

            ItemStack stack = new ItemStack(item);
            overlays.add(c -> c.drawItem(stack, x + 2, y + 2));
        }

        prev.active = page > 0;
        next.active = page < totalPages - 1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        page = GuiUtil.clamp(page + (verticalAmount > 0 ? -1 : 1), 0, totalPages - 1);
        rebuildGrid();
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Consumer<DrawContext> o : overlays) o.accept(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + "/" + totalPages),
                width / 2, height - 22, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
