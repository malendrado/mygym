package com.cortesdev.mygym.models.dto;

/** Vista del socio de los datos bancarios de SU gym (GET /api/me/gym/bank-transfer) — nunca
 *  se expone en la página pública de alta (/j/{slug}), solo al socio autenticado eligiendo
 *  cómo pagar. `configured` es false (y el resto de los campos null) si el admin no cargó
 *  los 4 campos clave todavía — el frontend usa eso para ocultar la opción de transferencia. */
public record BankTransferInfoResponse(
        boolean configured,
        String bankName,
        String accountType,
        String accountNumber,
        String holderRut,
        String holderName,
        String confirmationEmail) {}
