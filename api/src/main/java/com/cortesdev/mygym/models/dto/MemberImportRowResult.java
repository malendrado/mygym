package com.cortesdev.mygym.models.dto;

/** Resultado de una fila procesada por MemberService.importMembers — cada fila es independiente,
 *  el error de una nunca tumba el resto del lote. */
public record MemberImportRowResult(String email, boolean success, String error) {}
