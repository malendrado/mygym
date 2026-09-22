package com.cortesdev.mygym.models.dto;

/**
 * Vista completa de un socio reservado en una clase — solo para admins (gym-admin/super-admin).
 * reservationId identifica la reserva puntual (no el socio) — lo necesita el admin para poder
 * cancelarla (ver GymAdminController/GymController, endpoint de cancelar reserva).
 */
public record AttendeeResponse(Long id, String name, String email, String photoUrl, Long reservationId) {}
