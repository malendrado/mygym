package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.MemberWorkoutLog;
import java.time.LocalDate;

/** Un registro de rutina junto con la fecha de su clase (que vive en la reserva), leídos en una sola consulta. */
public record LoggedClass(MemberWorkoutLog log, LocalDate classDate) {}
