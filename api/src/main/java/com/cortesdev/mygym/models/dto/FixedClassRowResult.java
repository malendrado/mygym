package com.cortesdev.mygym.models.dto;

/** Resultado de validar UNA fila (misma posición que en el request). status = OK | WARNING | ERROR. */
public record FixedClassRowResult(int index, String email, String memberName, String status, String message) {}
