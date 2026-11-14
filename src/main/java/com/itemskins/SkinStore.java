package com.itemskins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stores imported skins (PNG files in config/itemskins/skins) and the
 * presets / tabs (config/itemskins/data.json).
 */
public final class SkinStore {
    public static final class Skin {
        public String id;          // sanitized, used for file + resource names
        public String name;        // display name
        public String tab = "";    // "" = not in any custom tab (still visible under "All")
    }

    public static final class Preset {
        public String name;
        public String tab = "";
        /** item id (e.g. minecraft:diamond_sword) -> skin id */
        public Map<String, String> items = new LinkedHashMap<>();
    }

    public static final class Data {
        public boolean enabled = true;
        public String activePreset = "";
        public List<Skin> skins = new ArrayList<>();
        public List<Preset> presets = new ArrayList<>();
        public List<String> skinTabs = new ArrayList<>();
        public List<String> presetTabs = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_PNG_BYTES = 16L * 1024 * 1024;

    public static Data data = new Data();

    private SkinStore() {}

    // ---------------------------------------------------------------- paths

    public static Path rootDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("itemskins");
    }

    public static Path skinsDir() {
        return rootDir().resolve("skins");
    }

    public static Path skinFile(String id) {
        return skinsDir().resolve(id + ".png");
    }

    private static Path dataFile() {
        return rootDir().resolve("data.json");
    }

    // ---------------------------------------------------------- load / save

    public static void load() {
        try {
            Files.createDirectories(skinsDir());
            if (Files.exists(dataFile())) {
                try (Reader r = Files.newBufferedReader(dataFile(), StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) data = d;
                }
            }
        } catch (Exception e) {
            ItemSkinsClient.LOGGER.error("Could not read Item Skins data", e);
        }
        normalize();
        adoptLooseFiles();
        data.skins.removeIf(s -> !Files.exists(skinFile(s.id)));
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(rootDir());
            try (Writer w = Files.newBufferedWriter(dataFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            ItemSkinsClient.LOGGER.error("Could not save Item Skins data", e);
        }
    }

    private static void normalize() {
        if (data.skins == null) data.skins = new ArrayList<>();
        if (data.presets == null) data.presets = new ArrayList<>();
        if (data.skinTabs == null) data.skinTabs = new ArrayList<>();
        if (data.presetTabs == null) data.presetTabs = new ArrayList<>();
        if (data.activePreset == null) data.activePreset = "";
        for (Skin s : data.skins) if (s.tab == null) s.tab = "";
        for (Preset p : data.presets) {
            if (p.tab == null) p.tab = "";
            if (p.items == null) p.items = new LinkedHashMap<>();
        }
    }

    /** PNGs dropped straight into config/itemskins/skins show up automatically. */
    private static void adoptLooseFiles() {
        try (Stream<Path> files = Files.list(skinsDir())) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                String base = stripExt(fn);
                if (findSkin(base) != null) continue;
                String clean = sanitize(base);
                String id = clean;
                if (!clean.equals(base) || fn.endsWith(".PNG")) {
                    int n = 2;
                    while (findSkin(id) != null || Files.exists(skinFile(id))) id = clean + "_" + n++;
                    Files.move(p, skinFile(id));
                }
                Skin s = new Skin();
                s.id = id;
                s.name = base;
                data.skins.add(s);
            }
        } catch (IOException e) {
            ItemSkinsClient.LOGGER.warn("Could not scan skins folder", e);
        }
    }

    // --------------------------------------------------------------- skins

    public static Skin findSkin(String id) {
        if (id == null) return null;
        for (Skin s : data.skins) if (s.id.equals(id)) return s;
        return null;
    }

    public static Skin importSkin(Path src) throws IOException {
        if (!Files.isRegularFile(src)) throw new IOException("File not found: " + src);
        if (Files.size(src) > MAX_PNG_BYTES) throw new IOException("File is too large: " + src.getFileName());
        byte[] b = Files.readAllBytes(src);
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        if (!png) throw new IOException("Not a PNG: " + src.getFileName());

        String base = stripExt(src.getFileName().toString());
        String clean = sanitize(base);
        String id = clean;
        int n = 2;
        while (findSkin(id) != null || Files.exists(skinFile(id))) id = clean + "_" + n++;

        Files.createDirectories(skinsDir());
        Files.write(skinFile(id), b);

        Skin s = new Skin();
        s.id = id;
        s.name = base;
        data.skins.add(s);
        return s;
    }

    public static void deleteSkin(Skin s) {
        data.skins.remove(s);
        try {
            Files.deleteIfExists(skinFile(s.id));
        } catch (IOException e) {
            ItemSkinsClient.LOGGER.warn("Could not delete skin file", e);
        }
        for (Preset p : data.presets) p.items.values().removeIf(v -> v.equals(s.id));
        save();
    }

    // ------------------------------------------------------------- presets

    public static Preset findPreset(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Preset p : data.presets) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    public static Preset active() {
        return findPreset(data.activePreset);
    }

    public static Preset createPreset(String name, String tab) {
        String base = name.trim();
        String cand = base;
        int n = 2;
        while (findPreset(cand) != null) cand = base + " " + n++;
        Preset p = new Preset();
        p.name = cand;
        p.tab = tab == null ? "" : tab;
        data.presets.add(p);
        if (data.activePreset.isEmpty()) data.activePreset = p.name;
        save();
        return p;
    }

    // ------------------------------------------------------------- helpers

    public static String sanitize(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        if (r.length() > 48) r = r.substring(0, 48);
        return r.isEmpty() ? "skin" : r;
    }

    private static String stripExt(String fn) {
        int i = fn.lastIndexOf('.');
        return i > 0 ? fn.substring(0, i) : fn;
    }
}
