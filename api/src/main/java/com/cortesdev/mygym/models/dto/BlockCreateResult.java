package com.cortesdev.mygym.models.dto;

/** Resultado de un bloque procesado por GymService.addBlocks — cada bloque es independiente,
 *  el error de uno (ej. horario inválido) nunca tumba el resto del lote. Mismo criterio que
 *  MemberImportRowResult; acá no hay un identificador natural por fila (a diferencia del email
 *  de un socio), así que el frontend correlaciona por posición en el array. */
public record BlockCreateResult(boolean success, BlockResponse block, String error) {}
