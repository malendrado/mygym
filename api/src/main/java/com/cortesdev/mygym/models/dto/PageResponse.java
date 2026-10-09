package com.cortesdev.mygym.models.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Contrato común de listas paginadas (reservas pasadas del socio y, en las siguientes etapas,
 * socios/gimnasios/auditoría) — el frontend usa el mismo tipo Page<T> para todas.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, boolean hasNext) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
    }
}
