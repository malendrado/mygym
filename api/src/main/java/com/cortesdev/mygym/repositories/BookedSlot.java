package com.cortesdev.mygym.repositories;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Una reserva vigente reducida a lo que cuentan los resúmenes de asistencia (clase, socio, fecha y si hizo check-in).
 * Se trae con una proyección en vez de la entidad completa: son miles de filas por llamada y Hibernate no necesita
 * hidratarlas ni vigilarlas para contarlas.
 */
public record BookedSlot(Long gymBlockId, Long memberId, LocalDate classDate, Instant checkedInAt) {}
