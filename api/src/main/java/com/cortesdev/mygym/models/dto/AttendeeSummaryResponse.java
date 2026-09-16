package com.cortesdev.mygym.models.dto;

/**
 * Vista reducida de un socio reservado en una clase, para OTROS socios (no
 * admins) — nunca incluye email ni apellido, a propósito: un socio no debe
 * poder identificar/contactar a otro solo por ver quién más reservó.
 */
public record AttendeeSummaryResponse(String firstName, String photoUrl) {}
