package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unidades puras: el CSV de desvinculación se abre en Excel, así que ningún dato escrito por un
 *  usuario (nombre, dirección, banco) puede quedar como fórmula. */
class CsvFieldTest {

    @Test
    void cellsStartingWithFormulaCharactersAreNeutralized() {
        for (String dangerous : new String[] {"=HYPERLINK(\"http://x\")", "+cmd|' /C calc'!A0", "-2+3", "@SUM(A1)", "\tx", "\rx"}) {
            String cell = GymDisconnectionService.csvField(dangerous);
            // Si además lleva comillas o comas, el CSV la envuelve en comillas: el apóstrofo va justo después.
            String content = cell.startsWith("\"") ? cell.substring(1) : cell;
            assertThat(content).as(dangerous).startsWith("'");
        }
    }

    @Test
    void normalTextIsKeptAndQuotedOnlyWhenNeeded() {
        assertThat(GymDisconnectionService.csvField("Camila Rojas")).isEqualTo("Camila Rojas");
        assertThat(GymDisconnectionService.csvField("Av. Siempre Viva 742, Ñuñoa")).isEqualTo("\"Av. Siempre Viva 742, Ñuñoa\"");
        assertThat(GymDisconnectionService.csvField("12.345.678-5")).isEqualTo("12.345.678-5");
        assertThat(GymDisconnectionService.csvField(null)).isEmpty();
    }
}
