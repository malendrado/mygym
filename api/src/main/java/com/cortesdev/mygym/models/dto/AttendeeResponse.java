package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.CheckinMethod;
import java.time.Instant;

/**
 * Vista completa de un socio reservado en una clase — solo para admins (gym-admin/profesor/super-admin).
 * reservationId identifica la reserva puntual (no el socio) — lo necesita el admin para poder
 * cancelarla (ver GymAdminController/GymController, endpoint de cancelar reserva) o marcar su
 * asistencia a mano. checkedInAt/checkinMethod dicen si vino y cómo (null = sin marcar); phone sirve
 * para escribirle por WhatsApp a quien no vino.
 */
public record AttendeeResponse(
        Long id,
        String name,
        String email,
        String photoUrl,
        Long reservationId,
        Instant checkedInAt,
        CheckinMethod checkinMethod,
        String phone) {}
