package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** apiKey/secretKey: enviar null o vacío para NO CAMBIAR el valor ya guardado — como nunca se
 *  devuelven en claro al frontend (ver FlowAccountResponse), no hay forma de "reenviar" el
 *  valor actual; solo se sobreescribe cuando llega un valor no vacío. */
public record FlowAccountUpdateRequest(
        @Size(max = 20) String companyRut,
        @Size(max = 160) String companyName,
        @Size(max = 160) String businessActivity,
        @Size(max = 200) String companyAddress,
        @Size(max = 80) String vatCondition,
        @Size(max = 120) String legalRepName,
        @Size(max = 20) String legalRepRut,
        @Size(max = 30) String legalRepPhone,
        @Email @Size(max = 160) String contactEmail,
        @Size(max = 120) String contactName,
        @Size(max = 30) String contactPhone,
        String apiKey,
        String secretKey) {}
