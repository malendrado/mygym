package com.cortesdev.mygym.models.dto;

/** Vista completa de un socio reservado en una clase — solo para admins (gym-admin/super-admin). */
public record AttendeeResponse(Long id, String name, String email, String photoUrl) {}
