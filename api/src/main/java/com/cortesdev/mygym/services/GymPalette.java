package com.cortesdev.mygym.services;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Single source of truth (backend side) for the 12 dark gym theme colors and their
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
            new Entry("plum", "#c15aff", "#24003d"),
            // Agregados 2026-10-10 (ver PALETTES en gym-theme.ts): texto oscuro #0B1218, ≥5.4:1 sobre cada uno.
            new Entry("rojo-competencia", "#FF4D4D", "#0B1218"),
            new Entry("cobre", "#D98B5F", "#0B1218"),
            new Entry("mandarina", "#FDBA74", "#0B1218"),
            new Entry("limon", "#FDE047", "#0B1218"),
            new Entry("salvia", "#A3C9A8", "#0B1218"),
            new Entry("oliva", "#B5C48A", "#0B1218"),
            new Entry("arena", "#E6C9A8", "#0B1218"),
            new Entry("durazno", "#FFB4A2", "#0B1218"),
            new Entry("rosa-polvo", "#F4B6C2", "#0B1218"),
            new Entry("verde-bosque", "#5FBF7A", "#0B1218"),
            new Entry("azul-acero", "#7FB2E5", "#0B1218"),
            new Entry("agua", "#38BDF8", "#0B1218"),
            new Entry("cielo", "#7DD3FC", "#0B1218"),
            new Entry("hielo", "#E5EEF5", "#0B1218"),
            new Entry("oro", "#E3B04B", "#0B1218"),
            new Entry("lavanda", "#C4B5FD", "#0B1218"));

    // Paletas de modo CLARO — a diferencia de ALL (acentos libres sobre una superficie
    // oscura derivada por hue, ver deriveSurfaceTint en el frontend), estas son 4 combos
    // curados a mano (fondo/tarjeta claros específicos, no derivados) porque la fórmula
    // de contraste de luminanceContrast() no es lo bastante precisa para garantizar
    // texto blanco legible sobre CUALQUIER acento en un botón — se verificó cada una
    // contra la fórmula real de contraste WCAG (≥4.5:1 texto/fondo, texto/tarjeta, y
    // blanco sobre el botón de acento) antes de fijarlas acá. Mirrors LIGHT_PALETTES en
    // app/src/app/core/utils/gym-theme.ts — si una cambia, actualizar la otra.
    public static final List<Entry> LIGHT_ALL = List.of(
            new Entry("amanecer", "#C2410C", "#FFFFFF"),
            new Entry("oceano", "#2563EB", "#FFFFFF"),
            new Entry("menta", "#047857", "#FFFFFF"),
            new Entry("frambuesa", "#DB2777", "#FFFFFF"),
            // Agregados 2026-10-10 (ver LIGHT_PALETTES en gym-theme.ts): texto blanco ≥4.9:1 sobre cada uno.
            new Entry("rojo-potencia", "#B91C1C", "#FFFFFF"),
            new Entry("burdeos", "#9F1239", "#FFFFFF"),
            new Entry("mostaza", "#854D0E", "#FFFFFF"),
            new Entry("bosque", "#15803D", "#FFFFFF"),
            new Entry("turquesa-profundo", "#0F766E", "#FFFFFF"),
            new Entry("salvia-clara", "#4D7C0F", "#FFFFFF"),
            new Entry("arcilla", "#B45309", "#FFFFFF"),
            new Entry("cafe", "#78350F", "#FFFFFF"),
            new Entry("agua-clara", "#0369A1", "#FFFFFF"),
            new Entry("indigo-claro", "#4338CA", "#FFFFFF"),
            new Entry("azul-marino", "#1E3A8A", "#FFFFFF"),
            new Entry("violeta-claro", "#7C3AED", "#FFFFFF"),
            new Entry("grafito", "#1F2937", "#FFFFFF"),
            new Entry("pizarra", "#334155", "#FFFFFF"),
            new Entry("dorado", "#A16207", "#FFFFFF"),
            new Entry("vino", "#7F1D1D", "#FFFFFF"),
            new Entry("ciruela-clara", "#86198F", "#FFFFFF"));

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

    /** Contrast color for a known palette hex (dark or light), or a sensible default
     *  (luminance heuristic) for a free color outside both lists. */
    public static String contrastFor(String hex) {
        if (hex == null) {
            return DEFAULT_CONTRAST;
        }
        return contrastForKnown(ALL, hex)
                .or(() -> contrastForKnown(LIGHT_ALL, hex))
                .orElseGet(() -> luminanceContrast(hex));
    }

    private static Optional<String> contrastForKnown(List<Entry> palette, String hex) {
        return palette.stream().filter(e -> e.hex().equalsIgnoreCase(hex)).map(Entry::contrast).findFirst();
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
