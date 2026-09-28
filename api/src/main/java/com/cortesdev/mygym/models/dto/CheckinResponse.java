package com.cortesdev.mygym.models.dto;

import java.util.List;

/** classLabels: casi siempre 1 elemento — más de uno solo si el socio tiene, contra toda
 *  expectativa, dos reservas simultáneas activas en este mismo instante. */
public record CheckinResponse(List<String> classLabels) {}
