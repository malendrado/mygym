package com.cortesdev.mygym.models.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Tope de 100 filas por request — el frontend manda el Excel en bloques de ~20 (ver
 *  ImportMembersModal), este límite es solo una red de seguridad por si un bug del cliente
 *  manda un bloque gigante por error. */
public record MemberImportRequest(@NotEmpty @Size(max = 100) @Valid List<MemberImportRow> rows) {}
