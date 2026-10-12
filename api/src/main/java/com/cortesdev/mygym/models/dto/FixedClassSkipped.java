package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;

/** Una fecha que la generación de clases fijas NO reservó, con el motivo (ej. "Cierre: corte de luz"). */
public record FixedClassSkipped(LocalDate date, String label, String reason) {}
