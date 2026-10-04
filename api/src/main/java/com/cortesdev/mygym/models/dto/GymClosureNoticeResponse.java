package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;

/** Banner para el socio en /member — el cierre activo o próximo más cercano de su gym, si hay alguno. */
public record GymClosureNoticeResponse(LocalDate startDate, LocalDate endDate, String reason, boolean wholeDays) {}
