package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Lo que puede editar el GYM_ADMIN de su propia cuenta Pago Online (PUT
 *  /api/gym-admin/gym/flow-account) — todo lo de FlowAccountUpdateRequest MENOS apiKey/
 *  secretKey: esas dos las carga únicamente el super-admin, una vez que estos datos ya
 *  estén completos (ver GymService.updateFlowAccountDetails). */
public record FlowAccountDetailsUpdateRequest(
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
        @Size(max = 30) String contactPhone) {}
