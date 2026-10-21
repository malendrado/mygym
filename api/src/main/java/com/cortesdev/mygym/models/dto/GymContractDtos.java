package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Contratos de la API de firma del contrato de servicios por el administrador del gimnasio. */
public final class GymContractDtos {

    private GymContractDtos() {}

    /** Lo que muestra la pantalla de firma. `blockedReason` no nulo = todavia no se puede firmar. */
    public record ContractView(
            String version,
            boolean signed,
            Instant signedAt,
            String signerName,
            String signerCapacity,
            /** Texto del contrato con los datos del gimnasio y el plan; los del firmante quedan en blanco hasta pedir el codigo. */
            String text,
            String blockedReason,
            /** Correo al que llegara el codigo, con la parte local tapada (j***@dominio.cl). */
            String maskedEmail,
            boolean certificateNeeded) {}

    public record CodeRequest(
            @NotBlank @Size(min = 3, max = 120) String signerName,
            @NotBlank @Size(max = 20) String signerRut,
            @NotBlank @Pattern(regexp = "PERSON_OWNER|LEGAL_REP|ATTORNEY") String capacity,
            @AssertTrue(message = "Debes declarar que eres mayor de edad.") boolean adult,
            @AssertTrue(message = "Debes declarar que tienes facultades para obligar al gimnasio.") boolean powers) {}

    public record CodeResponse(String maskedEmail) {}

    public record SignRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos.") String code) {}

    /** Estado del contrato dentro de la respuesta de suscripcion: lo ven el administrador y el super-admin. */
    public record ContractSummary(
            boolean required,
            boolean signed,
            String version,
            Instant signedAt,
            String signerName,
            String signerCapacity,
            boolean certificateNeeded) {}
}
