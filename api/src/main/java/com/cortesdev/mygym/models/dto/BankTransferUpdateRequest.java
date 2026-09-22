package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record BankTransferUpdateRequest(
        @Size(max = 60) String bankName,
        @Size(max = 30) String accountType,
        @Size(max = 40) String accountNumber,
        @Size(max = 20) String holderRut,
        @Size(max = 120) String holderName,
        @Email @Size(max = 160) String confirmationEmail) {}
