package com.itemskins.gui;

import com.itemskins.SkinStore;
import com.itemskins.SkinStore.Skin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.itemskins.gui.GuiUtil.btn;

/** Pick one of the imported skins (with previews). */
public class SkinPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Skin> onPick;
    private final List<ClickableWidget> gridWidgets = new ArrayList<>();
    private final List<Consumer<DrawContext>> overlays = new ArrayList<>();

    private String query = "";
    private int page;
    private int perPage = 2;
    private int totalPages = 1;
    private int emptyX, emptyY;
    private boolean empty;
    private ButtonWidget prev, next;

    public SkinPickerScreen(Screen parent, Consumer<Skin> onPick) {
        super(Text.literal("Pick a skin"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        gridWidgets.clear();

        TextFieldWidget search = new TextFieldWidget(textRenderer, (width - 220) / 2, 10, 220, 20, Text.empty());
        search.setMaxLength(64);
        search.setPlaceholder(Text.literal("Search skins..."));
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
        List<Skin> list = new ArrayList<>();
        for (Skin s : SkinStore.data.skins) {
            if (q.isEmpty() || s.name.toLowerCase(Locale.ROOT).contains(q)) list.add(s);
        }

        int cw = Math.min(width - 16, 460);
        int left = (width - cw) / 2;
        int colW = (cw - 4) / 2;
        int top = 40;
        int bottom = height - 36;
        int rows = Math.max(1, (bottom - top) / 24);
        perPage = rows * 2;
        totalPages = GuiUtil.pages(list.size(), perPage);
        page = GuiUtil.clamp(page, 0, totalPages - 1);

        empty = list.isEmpty();
        emptyX = left;
        emptyY = top + 4;

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < list.size(); i++) {
            Skin s = list.get(start + i);
            int x = left + (i % 2) * (colW + 4);
            int y = top + (i / 2) * 24;
            String label = textRenderer.trimToWidth(s.name, colW - 24 - 10);
            ButtonWidget b = btn(label, x + 24, y, colW - 24, 20, bb -> {
                client.setScreen(parent);
                onPick.accept(s);
            });
            addDrawableChild(b);
            gridWidgets.add(b);
            overlays.add(c -> GuiUtil.drawSkin(c, s.id, x + 2, y + 2, 16));
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
        if (empty) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("No skins found. Import some from the main menu first."),
                    emptyX, emptyY, 0xFFAAAAAA);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + "/" + totalPages),
                width / 2, height - 22, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
