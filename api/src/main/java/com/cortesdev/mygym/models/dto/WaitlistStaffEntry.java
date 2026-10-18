package com.cortesdev.mygym.models.dto;

import java.time.Instant;

/** Un socio en la lista de espera de una clase, tal como lo ve el equipo (dueño/profesor) en la Agenda.
 *  Lleva nombre y celular: SOLO se arma para endpoints de staff, nunca para socio, público ni TV.
 *  position parte en 1 (el primero de la fila); phone es null si el socio no lo tiene. */
public record WaitlistStaffEntry(Long memberId, String name, String phone, int position, Instant joinedAt) {}
