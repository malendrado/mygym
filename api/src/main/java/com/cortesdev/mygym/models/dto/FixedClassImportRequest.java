package com.cortesdev.mygym.models.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Para /validate (archivo entero, no escribe nada) y para el import confirmado (en bloques, con
 *  TODAS las filas de cada socio juntas: el archivo reemplaza el set completo de cada socio). */
public record FixedClassImportRequest(@NotEmpty @Size(max = 1000) List<@Valid FixedClassImportRow> rows) {}
