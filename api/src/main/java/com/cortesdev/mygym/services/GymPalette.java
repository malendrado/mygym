package com.cortesdev.mygym.services;

import java.util.List;
import java.util.Locale;

/**
 * Single source of truth (backend side) for the 7 gym theme colors and their
 * matching high-contrast text color. Mirrors the PALETTES constant in
 * app/src/app/pages/gym-admin/gym-admin.ts — if one changes, update the other.
 */
public final class GymPalette {

    public record Entry(String key, String hex, String contrast) {}

    public static final List<Entry> ALL = List.of(
            new Entry("lime", "#c6ff3d", "#1a2b00"),
            new Entry("blue", "#3da5ff", "#001a33"),
            new Entry("rose", "#ff5d73", "#330008"),
            new Entry("gold", "#ffb23d", "#331d00"),
            new Entry("emerald", "#2de6a0", "#00291a"),
            new Entry("violet", "#b98bff", "#1c0d33"),
            new Entry("cyan", "#3de6e6", "#002626"),
            new Entry("orange", "#ff7a3d", "#331500"),
            new Entry("indigo", "#6d7bff", "#05073d"),
            new Entry("fuchsia", "#ff5cb8", "#330019"),
            new Entry("turquoise", "#2dd4bf", "#00211c"),
            new Entry("plum", "#c15aff", "#24003d"));

    private static final String DEFAULT_HEX = "#c6ff3d";
    private static final String DEFAULT_CONTRAST = "#1a2b00";

    private GymPalette() {}

    public static String hexForKey(String key) {
        return ALL.stream()
                .filter(e -> e.key().equalsIgnoreCase(key))
                .map(Entry::hex)
                .findFirst()
                .orElse(null);
    }

    /** Contrast color for a known palette hex, or a sensible default for anything else. */
    public static String contrastFor(String hex) {
        if (hex == null) {
            return DEFAULT_CONTRAST;
        }
        return ALL.stream()
                .filter(e -> e.hex().equalsIgnoreCase(hex))
                .map(Entry::contrast)
                .findFirst()
                .orElseGet(() -> luminanceContrast(hex));
    }

    public static String defaultHex() {
        return DEFAULT_HEX;
    }

    /** Fallback for a theme color outside the 7 known options: pick black/white by relative luminance. */
    private static String luminanceContrast(String hex) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
            return luminance > 0.6 ? "#1a2b00" : "#eaf6f7";
        } catch (Exception e) {
            return DEFAULT_CONTRAST;
        }
    }

    public static String initialOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.strip().substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
