package com.itemskins.gui;

import com.itemskins.SkinPack;
import com.itemskins.SkinStore;
import com.itemskins.SkinStore.Preset;
import com.itemskins.SkinStore.Skin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.itemskins.gui.GuiUtil.btn;
import static com.itemskins.gui.GuiUtil.clamp;
import static com.itemskins.gui.GuiUtil.pages;

/**
 * The Item Skins menu.
 *
 *  Skins mode   - browse imported skins, import new PNGs (path field or drag & drop), sort them into tabs,
 *                 assign a skin to an item.
 *  Presets mode - named sets of item -> skin assignments, also sortable into tabs. One preset is active.
 */
public class MainScreen extends Screen {
    private enum Mode { SKINS, PRESETS }

    // remembered between openings
    private static Mode mode = Mode.SKINS;
    private static String skinTab = "";
    private static String presetTab = "";
    private static String selectedSkin = null;
    private static String selectedPreset = null;

    private final Screen parent;
    private final List<Consumer<DrawContext>> overlays = new ArrayList<>();

    private int skinPage, presetPage, assignPage, tabOffset;
    private int skinsPerPage = 2, presetsPerPage = 1, assignsPerPage = 1;
    private int curPage, totalPages = 1;
    private int presetColRight;
    private int statusX, statusW, pageCx, pageLabelY;
    private String inputText = "", tabText = "";
    private String status = "";
    private boolean dirty;

    public MainScreen(Screen parent) {
        super(Text.literal("Item Skins"));
        this.parent = parent;
    }

    // ================================================================ layout

    @Override
    protected void init() {
        overlays.clear();
        SkinStore.Data d = SkinStore.data;

        if (selectedPreset == null || SkinStore.findPreset(selectedPreset) == null) {
            Preset a = SkinStore.active();
            selectedPreset = a != null ? a.name : (d.presets.isEmpty() ? null : d.presets.get(0).name);
        }
        if (selectedSkin != null && SkinStore.findSkin(selectedSkin) == null) selectedSkin = null;

        int cw = Math.min(width - 16, 460);
        int left = (width - cw) / 2;
        int right = left + cw;
        int listTop = 68;
        int listBottom = height - 82;
        statusX = left;
        statusW = cw;

        // ---- row 1: mode switch, toggle, apply
        addDrawableChild(btn(mode == Mode.SKINS ? "[Skins]" : "Skins", left, 8, 70, 20, b -> {
            mode = Mode.SKINS;
            tabOffset = 0;
            inputText = "";
            clearAndInit();
        }));
        addDrawableChild(btn(mode == Mode.PRESETS ? "[Presets]" : "Presets", left + 74, 8, 70, 20, b -> {
            mode = Mode.PRESETS;
            tabOffset = 0;
            inputText = "";
            clearAndInit();
        }));
        addDrawableChild(btn("Mod: " + (d.enabled ? "ON" : "OFF"), right - 144, 8, 80, 20, b -> {
            d.enabled = !d.enabled;
            SkinStore.save();
            applyNow();
        }));
        addDrawableChild(btn(dirty ? "Apply *" : "Apply", right - 60, 8, 60, 20, b -> applyNow()));

        // ---- row 2: tabs
        buildTabs(left, cw);

        // ---- list area
        if (mode == Mode.SKINS) buildSkins(left, cw, listTop, listBottom);
        else buildPresets(left, cw, listTop, listBottom);

        // ---- bottom rows
        buildBottom(left, right, cw);

        pageCx = right - 40;
        pageLabelY = height - 72;
    }

    // ----------------------------------------------------------------- tabs

    private List<String> currentTabs() {
        return mode == Mode.SKINS ? SkinStore.data.skinTabs : SkinStore.data.presetTabs;
    }

    private String currentTab() {
        return mode == Mode.SKINS ? skinTab : presetTab;
    }

    private void setCurrentTab(String t) {
        if (mode == Mode.SKINS) {
            skinTab = t;
            skinPage = 0;
        } else {
            presetTab = t;
            presetPage = 0;
        }
    }

    private void buildTabs(int left, int cw) {
        List<String> tabs = new ArrayList<>();
        tabs.add("");
        tabs.addAll(currentTabs());
        int y = 32;
        int areaLeft = left + 24;
        int areaRight = left + cw - 24;
        tabOffset = clamp(tabOffset, 0, tabs.size() - 1);

        int x = areaLeft;
        int i = tabOffset;
        while (i < tabs.size()) {
            String t = tabs.get(i);
            String label = t.isEmpty() ? "All" : textRenderer.trimToWidth(t, 70);
            String shown = t.equals(currentTab()) ? "[" + label + "]" : label;
            int w = Math.max(36, textRenderer.getWidth(shown) + 12);
            if (x + w > areaRight && i > tabOffset) break;
            addDrawableChild(btn(shown, x, y, w, 20, b -> {
                setCurrentTab(t);
                clearAndInit();
            }));
            x += w + 2;
            i++;
        }
        boolean more = i < tabs.size();

        ButtonWidget prev = btn("<", left, y, 20, 20, b -> {
            tabOffset--;
            clearAndInit();
        });
        prev.active = tabOffset > 0;
        ButtonWidget next = btn(">", left + cw - 20, y, 20, 20, b -> {
            tabOffset++;
            clearAndInit();
        });
        next.active = more;
        addDrawableChild(prev);
        addDrawableChild(next);
    }

    private void addTab() {
        String name = tabText.trim();
        if (name.isEmpty() || name.equalsIgnoreCase("all")) {
            status("Type a tab name first (not 'All').");
            return;
        }
        List<String> tabs = currentTabs();
        for (String t : tabs) {
            if (t.equalsIgnoreCase(name)) {
                status("That tab already exists.");
                return;
            }
        }
        tabs.add(name);
        setCurrentTab(name);
        tabText = "";
        SkinStore.save();
        status("Tab '" + name + "' created.");
        clearAndInit();
    }

    private void deleteTab() {
        String t = currentTab();
        if (t.isEmpty()) {
            status("Select a custom tab first - 'All' can't be deleted.");
            return;
        }
        currentTabs().remove(t);
        for (Skin s : SkinStore.data.skins) if (s.tab.equals(t)) s.tab = "";
        for (Preset p : SkinStore.data.presets) if (p.tab.equals(t)) p.tab = "";
        setCurrentTab("");
        SkinStore.save();
        status("Tab '" + t + "' deleted. Its entries are still under 'All'.");
        clearAndInit();
    }

    private static String nextTab(String current, List<String> tabs) {
        List<String> opts = new ArrayList<>();
        opts.add("");
        opts.addAll(tabs);
        int idx = Math.max(0, opts.indexOf(current));
        return opts.get((idx + 1) % opts.size());
    }

    // ---------------------------------------------------------------- skins

    private List<Skin> filteredSkins() {
        List<Skin> out = new ArrayList<>();
        for (Skin s : SkinStore.data.skins) {
            if (skinTab.isEmpty() || skinTab.equals(s.tab)) out.add(s);
        }
        return out;
    }

    private void buildSkins(int left, int cw, int listTop, int listBottom) {
        List<Skin> list = filteredSkins();
        int rows = Math.max(1, (listBottom - listTop) / 24);
        skinsPerPage = rows * 2;
        totalPages = pages(list.size(), skinsPerPage);
        skinPage = clamp(skinPage, 0, totalPages - 1);
        curPage = skinPage;

        int colW = (cw - 4) / 2;
        int start = skinPage * skinsPerPage;
        for (int i = 0; i < skinsPerPage && start + i < list.size(); i++) {
            Skin s = list.get(start + i);
            int x = left + (i % 2) * (colW + 4);
            int y = listTop + (i / 2) * 24;
            boolean sel = s.id.equals(selectedSkin);
            String label = textRenderer.trimToWidth((sel ? "> " : "") + s.name, colW - 24 - 10);
            addDrawableChild(btn(label, x + 24, y, colW - 24, 20, b -> {
                selectedSkin = s.id;
                clearAndInit();
            }));
            overlays.add(c -> GuiUtil.drawSkin(c, s.id, x + 2, y + 2, 16));
        }

        if (list.isEmpty()) {
            overlays.add(c -> {
                c.drawTextWithShadow(textRenderer, Text.literal("No skins here yet."), left, listTop + 4, 0xFFAAAAAA);
                c.drawTextWithShadow(textRenderer, Text.literal("Paste a PNG path below and click Import,"),
                        left, listTop + 18, 0xFFAAAAAA);
                c.drawTextWithShadow(textRenderer, Text.literal("or drag & drop PNG files onto this window."),
                        left, listTop + 30, 0xFFAAAAAA);
            });
        }
    }

    // -------------------------------------------------------------- presets

    private List<Preset> filteredPresets() {
        List<Preset> out = new ArrayList<>();
        for (Preset p : SkinStore.data.presets) {
            if (presetTab.isEmpty() || presetTab.equals(p.tab)) out.add(p);
        }
        return out;
    }

    private void buildPresets(int left, int cw, int listTop, int listBottom) {
        List<Preset> list = filteredPresets();
        int leftW = Math.min(140, cw / 3);
        presetColRight = left + leftW;

        presetsPerPage = Math.max(1, (listBottom - listTop) / 22);
        presetPage = clamp(presetPage, 0, pages(list.size(), presetsPerPage) - 1);
        int start = presetPage * presetsPerPage;
        for (int i = 0; i < presetsPerPage && start + i < list.size(); i++) {
            Preset p = list.get(start + i);
            boolean isActive = p.name.equalsIgnoreCase(SkinStore.data.activePreset);
            boolean sel = p.name.equalsIgnoreCase(selectedPreset);
            String label = (isActive ? "[ON] " : "") + (sel ? "> " : "") + p.name;
            addDrawableChild(btn(textRenderer.trimToWidth(label, leftW - 10), left, listTop + i * 22, leftW, 20, b -> {
                selectedPreset = p.name;
                assignPage = 0;
                clearAndInit();
            }));
        }
        if (list.isEmpty()) {
            overlays.add(c -> c.drawTextWithShadow(textRenderer, Text.literal("No presets."), left, listTop + 4, 0xFFAAAAAA));
        }

        // ---- assignments of the selected preset
        Preset sel = SkinStore.findPreset(selectedPreset);
        int x0 = left + leftW + 8;
        int panelW = cw - leftW - 8;
        assignsPerPage = Math.max(1, (listBottom - listTop) / 24);

        if (sel == null) {
            totalPages = 1;
            curPage = 0;
            overlays.add(c -> c.drawTextWithShadow(textRenderer,
                    Text.literal("Create a preset with the field below."), x0, listTop + 4, 0xFFAAAAAA));
            return;
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(sel.items.entrySet());
        totalPages = pages(entries.size(), assignsPerPage);
        assignPage = clamp(assignPage, 0, totalPages - 1);
        curPage = assignPage;

        if (entries.isEmpty()) {
            overlays.add(c -> {
                c.drawTextWithShadow(textRenderer, Text.literal("This preset is empty."), x0, listTop + 4, 0xFFAAAAAA);
                c.drawTextWithShadow(textRenderer, Text.literal("Use 'Add item' or 'Held item'."), x0, listTop + 18, 0xFFAAAAAA);
            });
        }

        int s0 = assignPage * assignsPerPage;
        for (int i = 0; i < assignsPerPage && s0 + i < entries.size(); i++) {
            String itemId = entries.get(s0 + i).getKey();
            String skinId = entries.get(s0 + i).getValue();
            Skin skin = SkinStore.findSkin(skinId);
            int y = listTop + i * 24;

            int btnX = x0 + 22;
            int btnW = panelW - 22 - 24 - 24;
            String text = GuiUtil.name(itemId) + "  ->  " + (skin == null ? skinId + " (missing)" : skin.name);
            addDrawableChild(btn(textRenderer.trimToWidth(text, btnW - 10), btnX, y, btnW, 20, b ->
                    openSkinPicker(sel, itemId)));
            addDrawableChild(btn("x", x0 + panelW - 20, y, 20, 20, b -> {
                sel.items.remove(itemId);
                dirty = true;
                SkinStore.save();
                clearAndInit();
            }));

            Item item = GuiUtil.item(itemId);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                overlays.add(c -> c.drawItem(stack, x0 + 2, y + 2));
            }
            if (skin != null) {
                overlays.add(c -> GuiUtil.drawSkin(c, skin.id, btnX + btnW + 4, y + 2, 16));
            }
        }
    }

    // --------------------------------------------------------------- bottom

    private void buildBottom(int left, int right, int cw) {
        int yA = height - 78;
        int yB = height - 54;
        int yC = height - 30;

        // row A: tab management (left) + page nav (right)
        TextFieldWidget tabInput = new TextFieldWidget(textRenderer, left, yA, 90, 20, Text.empty());
        tabInput.setMaxLength(24);
        tabInput.setPlaceholder(Text.literal("New tab name"));
        tabInput.setText(tabText);
        tabInput.setChangedListener(s -> tabText = s);
        addDrawableChild(tabInput);
        addDrawableChild(btn("+ Tab", left + 94, yA, 44, 20, b -> addTab()));
        addDrawableChild(btn("- Tab", left + 142, yA, 44, 20, b -> deleteTab()));

        ButtonWidget pPrev = btn("<", right - 80, yA, 20, 20, b -> shiftPage(-1));
        pPrev.active = curPage > 0;
        ButtonWidget pNext = btn(">", right - 20, yA, 20, 20, b -> shiftPage(1));
        pNext.active = curPage < totalPages - 1;
        addDrawableChild(pPrev);
        addDrawableChild(pNext);

        // row B: actions
        if (mode == Mode.SKINS) {
            Skin sel = SkinStore.findSkin(selectedSkin);
            ButtonWidget assign = btn("Assign to item...", left, yB, 100, 20, b -> assignSkinFlow());
            assign.active = sel != null;
            addDrawableChild(assign);

            String tabLabel = sel == null ? "Move to tab" : "Tab: " + (sel.tab.isEmpty() ? "none" : sel.tab);
            ButtonWidget move = btn(textRenderer.trimToWidth(tabLabel, 110), left + 104, yB, 120, 20, b -> {
                sel.tab = nextTab(sel.tab, SkinStore.data.skinTabs);
                SkinStore.save();
                clearAndInit();
            });
            move.active = sel != null;
            addDrawableChild(move);

            ButtonWidget del = btn("Delete skin", left + 228, yB, 80, 20, b -> {
                SkinStore.deleteSkin(sel);
                selectedSkin = null;
                dirty = true;
                status("Skin deleted. Click Apply to refresh.");
                clearAndInit();
            });
            del.active = sel != null;
            addDrawableChild(del);
        } else {
            Preset sel = SkinStore.findPreset(selectedPreset);

            ButtonWidget activate = btn("Activate", left, yB, 54, 20, b -> {
                SkinStore.data.activePreset = sel.name;
                SkinStore.save();
                applyNow();
            });
            activate.active = sel != null;
            addDrawableChild(activate);

            ButtonWidget add = btn("Add item", left + 58, yB, 58, 20, b -> addItemFlow(sel));
            add.active = sel != null;
            addDrawableChild(add);

            ButtonWidget held = btn("Held item", left + 120, yB, 62, 20, b -> heldItemFlow(sel));
            held.active = sel != null;
            addDrawableChild(held);

            String tabLabel = sel == null ? "Move to tab" : "Tab: " + (sel.tab.isEmpty() ? "none" : sel.tab);
            ButtonWidget move = btn(textRenderer.trimToWidth(tabLabel, 100), left + 186, yB, 110, 20, b -> {
                sel.tab = nextTab(sel.tab, SkinStore.data.presetTabs);
                SkinStore.save();
                clearAndInit();
            });
            move.active = sel != null;
            addDrawableChild(move);

            ButtonWidget del = btn("Delete", left + 300, yB, 50, 20, b -> {
                SkinStore.data.presets.remove(sel);
                if (sel.name.equalsIgnoreCase(SkinStore.data.activePreset)) {
                    SkinStore.data.activePreset = "";
                    dirty = true;
                }
                selectedPreset = null;
                SkinStore.save();
                clearAndInit();
            });
            del.active = sel != null;
            addDrawableChild(del);
        }

        // row C: path / name input
        TextFieldWidget input = new TextFieldWidget(textRenderer, left, yC, cw - 84, 20, Text.empty());
        input.setMaxLength(512);
        input.setPlaceholder(Text.literal(mode == Mode.SKINS
                ? "Path to a .png (or drag & drop files here)"
                : "New preset name"));
        input.setText(inputText);
        input.setChangedListener(s -> inputText = s);
        addDrawableChild(input);
        addDrawableChild(btn(mode == Mode.SKINS ? "Import" : "New preset", right - 80, yC, 80, 20, b -> {
            if (mode == Mode.SKINS) importFromField();
            else createPresetFromField();
        }));
    }

    private void shiftPage(int dir) {
        if (mode == Mode.SKINS) skinPage += dir;
        else assignPage += dir;
        clearAndInit();
    }

    // -------------------------------------------------------------- actions

    private void status(String s) {
        status = s;
    }

    private void applyNow() {
        SkinPack.apply(client);
        dirty = false;
        status("Applied - reloading resources...");
        clearAndInit();
    }

    private void importFromField() {
        String raw = inputText.trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1); // Windows "Copy as path" adds quotes
        }
        if (raw.isEmpty()) {
            status("Paste a path to a .png, or drag files onto the window.");
            clearAndInit();
            return;
        }
        try {
            importPaths(List.of(Path.of(raw)));
        } catch (InvalidPathException e) {
            status("That doesn't look like a valid path.");
            clearAndInit();
        }
    }

    private void importPaths(List<Path> paths) {
        List<Path> files = new ArrayList<>();
        for (Path p : paths) {
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p, 2)) {
                    s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).forEach(files::add);
                } catch (IOException ignored) {
                }
            } else {
                files.add(p);
            }
        }

        int ok = 0;
        String error = null;
        for (Path p : files) {
            try {
                Skin s = SkinStore.importSkin(p);
                if (!skinTab.isEmpty()) s.tab = skinTab;
                selectedSkin = s.id;
                ok++;
            } catch (IOException e) {
                error = e.getMessage();
            }
        }

        if (ok > 0) {
            SkinStore.save();
            mode = Mode.SKINS;
            inputText = "";
            status("Imported " + ok + " skin" + (ok == 1 ? "" : "s") + " - reloading...");
            SkinPack.apply(client);
            dirty = false;
        } else {
            status(error != null ? error : "No PNG files found.");
        }
        clearAndInit();
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        importPaths(paths);
    }

    private void createPresetFromField() {
        String name = inputText.trim();
        if (name.isEmpty()) {
            status("Type a preset name first.");
            clearAndInit();
            return;
        }
        Preset p = SkinStore.createPreset(name, presetTab);
        selectedPreset = p.name;
        inputText = "";
        dirty = true;
        status("Preset '" + p.name + "' created.");
        clearAndInit();
    }

    /** Skins mode: pick an item, then put (selected skin -> item) into the active preset. */
    private void assignSkinFlow() {
        String skinId = selectedSkin;
        if (skinId == null) return;
        client.setScreen(new ItemPickerScreen(this, item -> {
            Preset p = SkinStore.active();
            if (p == null) p = SkinStore.findPreset(selectedPreset);
            if (p == null) {
                p = SkinStore.createPreset("Default", "");
                selectedPreset = p.name;
            }
            if (SkinStore.active() == null) SkinStore.data.activePreset = p.name;
            p.items.put(GuiUtil.id(item), skinId);
            dirty = true;
            SkinStore.save();
            status("Assigned to " + GuiUtil.name(item) + " in preset '" + p.name + "'. Click Apply.");
        }));
    }

    /** Presets mode: pick an item, then pick its skin. */
    private void addItemFlow(Preset p) {
        if (p == null) return;
        client.setScreen(new ItemPickerScreen(this, item -> openSkinPicker(p, GuiUtil.id(item))));
    }

    private void heldItemFlow(Preset p) {
        if (p == null) return;
        if (client.player == null) {
            status("Open this menu inside a world to use the held item.");
            return;
        }
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            status("You're not holding anything.");
            return;
        }
        openSkinPicker(p, GuiUtil.id(held.getItem()));
    }

    private void openSkinPicker(Preset p, String itemId) {
        client.setScreen(new SkinPickerScreen(this, skin -> {
            p.items.put(itemId, skin.id);
            dirty = true;
            SkinStore.save();
            status(GuiUtil.name(itemId) + " -> " + skin.name + ". Click Apply.");
        }));
    }

    // ------------------------------------------------------------ input etc

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int dir = verticalAmount > 0 ? -1 : 1;
        if (mode == Mode.SKINS) skinPage += dir;
        else if (mouseX < presetColRight) presetPage += dir;
        else assignPage += dir;
        skinPage = Math.max(0, skinPage);
        presetPage = Math.max(0, presetPage);
        assignPage = Math.max(0, assignPage);
        clearAndInit();
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Consumer<DrawContext> o : overlays) o.accept(ctx);
        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(status, statusW)),
                    statusX, 56, 0xFFFFD27F);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((curPage + 1) + "/" + totalPages),
                pageCx, pageLabelY, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (dirty) SkinPack.apply(client); // apply pending changes when leaving the menu
        client.setScreen(parent);
    }
}
