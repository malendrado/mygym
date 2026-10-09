package com.cortesdev.mygym.models.dto;

import java.util.List;

/**
 * Combina en una sola respuesta los 5 datos "de encabezado" que /member pedía antes como 5
 * llamadas HTTP separadas (gym, membresía, planes, datos bancarios, aviso de cierre) — cada
 * pageview de un socio disparaba hasta 7-8 requests en paralelo contra un pool Hikari de solo 5
 * conexiones, siendo /member la pantalla de mayor concurrencia real de toda la app (la abre cada
 * socio). Las 3 llamadas más pesadas (fotos, ocurrencias del calendario, mis reservas) quedan
 * aparte a propósito — son las que más tardan y las que menos urge pintar primero (auditoría de
 * performance 2026-10-09).
 */
public record MemberDashboardResponse(
        PublicGymResponse gym,
        MemberResponse membership,
        List<MemberPlanResponse> plans,
        BankTransferInfoResponse bankTransfer,
        GymClosureNoticeResponse closureNotice) {}
