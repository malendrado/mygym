package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Datos de mygym como empresa: contacto, empresa que contrata y cuenta para transferencias. */
public final class MygymSettingsDtos {

    private MygymSettingsDtos() {}

    /** Lo que está vigente (el guardado o, si falta, el valor de respaldo del servidor): así el formulario parte con lo que se usa hoy. */
    public record Response(
            String whatsapp,
            String contactEmail,
            String companyName,
            String companyRut,
            String legalRepName,
            String legalRepRut,
            String companyAddress,
            String bankHolder,
            String bankRut,
            String bankName,
            String bankAccountType,
            String bankAccountNumber,
            String bankEmail,
            Instant updatedAt) {}

    public record Request(
            @NotBlank @Pattern(regexp = "^[0-9]{8,15}$", message = "El WhatsApp va solo con dígitos y código de país (ej: 56912345678).") String whatsapp,
            @NotBlank @Email @Size(max = 160) String contactEmail,
            @NotBlank @Size(max = 160) String companyName,
            @NotBlank @Size(max = 20) String companyRut,
            @NotBlank @Size(max = 120) String legalRepName,
            @NotBlank @Size(max = 20) String legalRepRut,
            @NotBlank @Size(max = 240) String companyAddress,
            @Size(max = 160) String bankHolder,
            @Size(max = 20) String bankRut,
            @Size(max = 80) String bankName,
            @Size(max = 40) String bankAccountType,
            @Size(max = 40) String bankAccountNumber,
            @Email @Size(max = 160) String bankEmail) {}

    /** Contacto público (landing, FAQ, privacidad, avisos): no incluye nada de la empresa ni de la cuenta bancaria. */
    public record PublicContact(String whatsapp, String email) {}
}
