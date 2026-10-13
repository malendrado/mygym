package com.cortesdev.mygym.models.dto;

/** Lo único que ve quien abre el enlace de la guía: su nombre de pila y el branding de su gym. */
public record GuideResponse(String firstName, PublicGymResponse gym) {}
