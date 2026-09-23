package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Una fila del Excel de importación masiva (POST /api/gym-admin/members/import). planId/amountClp/expiresAt
 * van los tres juntos o los tres vacíos — el frontend ya valida esto antes de mandar (ver
 * ImportMembersModal), pero MemberService.importRow lo revalida igual porque el archivo pudo cambiar entre
 * la vista previa y la confirmación (carga en bloques, puede tardar varios minutos).
 */
public record MemberImportRow(
        @NotBlank String name,
        @NotBlank @Email String email,
        Long planId,
        Integer amountClp,
        Instant expiresAt,
        /** Solo aplica si el plan tiene cupo limitado (no "libre") — clases que el socio ya venía
         *  usando este mes en el sistema anterior del gym. Ver AppUser.usedSessionsAtImport. */
        Integer usedSessions) {}
