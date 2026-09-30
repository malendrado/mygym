package com.cortesdev.mygym.models.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Tope de 500 bloques por request — mismo criterio que MemberImportRequest (red de seguridad,
 *  no un límite que el uso normal alcance: el generador de series del frontend, en el caso más
 *  extremo, arma unos ~160-336 bloques). Reemplaza el fan-out de un POST por bloque que hacía
 *  BloqueSeriesModal/confirmSeries antes — un solo request con todos los bloques. */
public record BlockBatchCreateRequest(@NotEmpty @Size(max = 500) @Valid List<BlockCreateRequest> blocks) {}
