package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// cancellationWindowHours salió de acá (ver V36/BookingRulesRequest) — la ventana de
// reserva/cancelación ahora se edita en la pestaña Horarios, no en Mi marca.
public record GymIdentityUpdateRequest(
        @Size(max = 160) String tagline,
        @Size(max = 600) String description,
        // Este enlace se muestra a los socios. Antes el servidor guardaba cualquier texto y solo el front filtraba, así que una
        // llamada directa a la API podía dejar "javascript:..." u otro esquema. Se aceptan los mismos formatos que la página de
        // alta (join.ts): una URL http(s), "instagram.com/usuario" sin esquema, o solo "@usuario". null (sin enlace) es válido;
        // el front manda null cuando el campo queda vacío.
        @Size(max = 200)
                @Pattern(
                        regexp = "^(https?://[^\\s<>\"']+|(www\\.)?instagram\\.com/[^\\s<>\"']+|@?[A-Za-z0-9._]{1,30})$",
                        message = "El enlace de Instagram debe ser una URL (https://instagram.com/tugym) o tu @usuario")
                String instagramUrl,
        @Size(max = 30) String whatsappNumber) {}
