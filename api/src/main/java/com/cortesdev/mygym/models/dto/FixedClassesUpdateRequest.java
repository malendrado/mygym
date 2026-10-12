package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Reemplaza el set COMPLETO de clases fijas del socio (lista vacía = quitarlas todas). Una por
 *  día de la semana como máximo, así que 7 es el tope real. */
public record FixedClassesUpdateRequest(@NotNull @Size(max = 7) List<Long> blockIds) {}
