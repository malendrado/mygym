package com.cortesdev.mygym.models.dto;

/** Espejo ampliado de AttendeeSummaryResponse, solo para la pantalla de TV — decisión explícita
 *  del usuario de mostrar el apellido acá (a diferencia de "Ver quién reservó", que nunca lo
 *  muestra): lastName es null si el socio no tiene apellido registrado. El frontend decide
 *  cuánto mostrar según el espacio disponible — apellido completo en modo detallado (1-2
 *  clases simultáneas), solo la inicial en modo compacto (3+ simultáneas) y en las tarjetas
 *  chicas de anterior/próxima. planId viaja aparte de planName para que el frontend le asigne
 *  un color determinístico y estable a cada plan. */
public record TvAttendeeResponse(String firstName, String lastName, String photoUrl, String planName, Long planId) {}
