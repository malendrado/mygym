package com.cortesdev.mygym.repositories;

/** Projection de una native query (PageViewRepository.aggregateByGym) — Spring Data mapea las
 *  columnas por el alias SQL (gymId/gymName/gymSlug/total/last30d) al nombre del getter. */
public interface GymVisitAggregateRow {
    Long getGymId();

    String getGymName();

    String getGymSlug();

    Long getTotal();

    Long getLast30d();
}
