package com.cortesdev.mygym.models.dto;

/** Resultado de cargar las clases fijas de UN socio desde el Excel. */
public record FixedClassImportMemberResult(
        String email, String memberName, boolean ok, String message, int created, int skipped) {}
