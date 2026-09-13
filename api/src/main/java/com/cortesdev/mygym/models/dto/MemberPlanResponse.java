package com.cortesdev.mygym.models.dto;

/** Vista de un plan para el socio — sin gymId (ya lo conoce) ni active (solo se listan los activos). */
public record MemberPlanResponse(
        Long id, String name, String description, String category, Integer priceClp, Integer monthlyClasses) {}
