package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unidades puras: toda paleta ofrecida en "Mi marca" tiene que cumplir contraste WCAG AA (4.5:1). */
class GymPaletteTest {

    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double contrast(String a, String b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    @Test
    void everyLightPaletteHasReadableButtonText() {
        for (GymPalette.Entry e : GymPalette.LIGHT_ALL) {
            assertThat(contrast(e.contrast(), e.hex()))
                    .as("texto %s sobre acento claro %s (%s)", e.contrast(), e.hex(), e.key())
                    .isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    void everyDarkPaletteHasReadableButtonTextAndReadableAccentOnDarkSurface() {
        for (GymPalette.Entry e : GymPalette.ALL) {
            assertThat(contrast(e.contrast(), e.hex()))
                    .as("texto %s sobre acento %s (%s)", e.contrast(), e.hex(), e.key())
                    .isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    void keysAndHexesAreUniqueWithinEachList() {
        for (List<GymPalette.Entry> list : List.of(GymPalette.ALL, GymPalette.LIGHT_ALL)) {
            assertThat(list.stream().map(GymPalette.Entry::key).distinct().count()).isEqualTo(list.size());
            assertThat(list.stream().map(e -> e.hex().toLowerCase()).distinct().count()).isEqualTo(list.size());
        }
    }

    @Test
    void knownPalettesReturnTheirOwnContrast() {
        for (GymPalette.Entry e : GymPalette.LIGHT_ALL) {
            assertThat(GymPalette.contrastFor(e.hex())).isEqualTo(e.contrast());
        }
    }
}
