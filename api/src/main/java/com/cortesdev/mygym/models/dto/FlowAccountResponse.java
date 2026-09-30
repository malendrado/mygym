package com.cortesdev.mygym.models.dto;

/** Vista del super-admin de la cuenta Pago Online (Flow.cl) de un gym
 *  (GET/PUT /api/gyms/{id}/flow-account) — apiKey/secretKey NUNCA se devuelven en claro, solo
 *  enmascaradas (ver GymService.maskSecret); el super-admin solo las reemplaza escribiendo un
 *  valor nuevo, nunca las "reenvía" tal cual. `configured` es lo que decide si el gym ofrece
 *  "Pagar con Flow" a sus socios (ver PublicGymResponse.flowConfigured). */
public record FlowAccountResponse(
        boolean configured,
        String companyRut,
        String companyName,
        String businessActivity,
        String companyAddress,
        String vatCondition,
        String legalRepName,
        String legalRepRut,
        String legalRepPhone,
        String contactEmail,
        String contactName,
        String contactPhone,
        boolean hasApiKey,
        boolean hasSecretKey,
        String apiKeyMasked,
        String secretKeyMasked) {}
