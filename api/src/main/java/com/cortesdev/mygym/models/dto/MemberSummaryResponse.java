package com.cortesdev.mygym.models.dto;

/**
 * Conteos de las calugas de la pestaña Socios (total del gym, NO de la página visible) — con la
 * lista paginada ya no se pueden calcular contando filas en el navegador. Ver
 * MemberService.memberSummary.
 */
public record MemberSummaryResponse(
        long total, long active, long expiringSoon, long expired, long unpaid, long invitedPending) {}
